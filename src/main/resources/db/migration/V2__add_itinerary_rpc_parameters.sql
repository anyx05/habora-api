-- V2: Extend create_booking_safely to support itinerary (multi-stop) bookings.
--
-- IMPORTANT: This is a CREATE OR REPLACE of the existing function. The function
-- body below is reconstructed from the domain CLAUDE.md and known schema.
-- Before running, verify it matches your existing implementation — particularly
-- the confirmation code format, price calculation, and overlap detection logic.
--
-- Key behavioural change:
--   - When p_itinerary_id IS NULL (direct booking): status='confirmed', confirmation_code generated.
--   - When p_itinerary_id IS NOT NULL (itinerary booking): status='pending', confirmation_code=NULL.
--     The confirm_itinerary RPC handles final confirmation and code generation atomically.
--
-- Run manually in Supabase SQL editor (Flyway is disabled for this project).

CREATE OR REPLACE FUNCTION create_booking_safely(
    p_berth_id         uuid,
    p_customer_name    text,
    p_customer_email   text,
    p_vessel_name      text,
    p_vessel_length_m  numeric,
    p_vessel_draft_m   numeric,
    p_arrival_date     date,
    p_departure_date   date,
    p_notes            text    DEFAULT NULL,
    p_itinerary_id     uuid    DEFAULT NULL,
    p_stop_order       int     DEFAULT NULL
)
RETURNS TABLE (
    id                uuid,
    berth_id          uuid,
    customer_name     text,
    customer_email    text,
    vessel_name       text,
    vessel_length_m   numeric,
    vessel_draft_m    numeric,
    arrival_date      date,
    departure_date    date,
    status            text,
    total_price       numeric,
    notes             text,
    confirmation_code text,
    created_at        timestamptz,
    itinerary_id      uuid,
    stop_order        int
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
    v_berth           record;
    v_nights          int;
    v_total_price     numeric;
    v_status          text;
    v_confirmation    text;
    v_booking_id      uuid;
BEGIN
    -- Resolve berth
    SELECT * INTO v_berth
    FROM berths
    WHERE berths.id = p_berth_id AND is_active = true;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Berth not found' USING ERRCODE = 'P0001';
    END IF;

    -- Validate vessel dimensions against berth limits
    IF p_vessel_length_m > v_berth.max_length_m THEN
        RAISE EXCEPTION 'Vessel is too long for this berth (max % m)', v_berth.max_length_m
            USING ERRCODE = 'P0002';
    END IF;

    IF p_vessel_draft_m IS NOT NULL AND p_vessel_draft_m > v_berth.max_draft_m THEN
        RAISE EXCEPTION 'Vessel draft is too deep for this berth (max % m)', v_berth.max_draft_m
            USING ERRCODE = 'P0003';
    END IF;

    -- Check for overlapping non-cancelled bookings on this berth.
    -- Pending bookings in an itinerary do NOT block availability until confirmation.
    IF EXISTS (
        SELECT 1
        FROM bookings
        WHERE bookings.berth_id    = p_berth_id
          AND bookings.status  NOT IN ('cancelled', 'pending')
          AND bookings.arrival_date   < p_departure_date
          AND bookings.departure_date > p_arrival_date
    ) THEN
        RAISE EXCEPTION 'Berth is unavailable for the requested dates'
            USING ERRCODE = 'P0004';
    END IF;

    -- Derive status and confirmation code based on whether this is an itinerary booking
    IF p_itinerary_id IS NULL THEN
        v_status       := 'confirmed';
        v_confirmation := 'HBR-' || upper(substring(md5(gen_random_uuid()::text), 1, 8));
    ELSE
        v_status       := 'pending';
        v_confirmation := NULL;
    END IF;

    -- Calculate price
    v_nights      := p_departure_date - p_arrival_date;
    v_total_price := v_nights * v_berth.price_per_night;

    -- Insert booking
    INSERT INTO bookings (
        berth_id, customer_name, customer_email, vessel_name,
        vessel_length_m, vessel_draft_m, arrival_date, departure_date,
        status, total_price, notes, confirmation_code, itinerary_id, stop_order
    )
    VALUES (
        p_berth_id, p_customer_name, p_customer_email, p_vessel_name,
        p_vessel_length_m, p_vessel_draft_m, p_arrival_date, p_departure_date,
        v_status, v_total_price, p_notes, v_confirmation, p_itinerary_id, p_stop_order
    )
    RETURNING bookings.id INTO v_booking_id;

    RETURN QUERY
    SELECT b.id,
           b.berth_id,
           b.customer_name,
           b.customer_email,
           b.vessel_name,
           b.vessel_length_m,
           b.vessel_draft_m,
           b.arrival_date,
           b.departure_date,
           b.status,
           b.total_price,
           b.notes,
           b.confirmation_code,
           b.created_at,
           b.itinerary_id,
           b.stop_order
    FROM bookings b
    WHERE b.id = v_booking_id;
END;
$$;
