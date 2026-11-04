BEGIN;
GRANT USAGE ON SCHEMA public TO service_role;
GRANT SELECT ON public.profiles TO service_role;
GRANT SELECT, UPDATE ON public.spot_proposals TO service_role;
GRANT INSERT ON public.spots TO service_role;

CREATE OR REPLACE FUNCTION public.moderate_spot_proposal(
  p_proposal_id uuid,
  p_moderator_id uuid,
  p_decision text,
  p_reason text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $function$
DECLARE
  v_proposal public.spot_proposals%rowtype;
  v_spot_id text;
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = p_moderator_id AND role = 'moderator'
  ) THEN
    RAISE EXCEPTION USING ERRCODE = '42501', MESSAGE = 'NOT_MODERATOR';
  END IF;

  IF p_proposal_id IS NULL OR p_decision IS NULL
     OR p_decision NOT IN ('approved', 'rejected') THEN
    RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'INVALID_DECISION';
  END IF;

  IF p_decision = 'rejected' AND
     (p_reason IS NULL OR length(btrim(p_reason)) = 0 OR length(p_reason) > 1000) THEN
    RAISE EXCEPTION USING ERRCODE = '22023', MESSAGE = 'REASON_REQUIRED';
  END IF;

  SELECT * INTO v_proposal
  FROM public.spot_proposals
  WHERE id = p_proposal_id
  FOR UPDATE;

  IF NOT FOUND THEN
    RAISE EXCEPTION USING ERRCODE = 'P0002', MESSAGE = 'PROPOSAL_NOT_FOUND';
  END IF;

  IF v_proposal.status <> 'pending' THEN
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'PROPOSAL_ALREADY_REVIEWED';
  END IF;

  IF p_decision = 'approved' THEN
    v_spot_id := 'community:' || v_proposal.id::text;
    INSERT INTO public.spots (
      id, name, sport_type, latitude, longitude,
      address, surface, lighting, equipment, source
    ) VALUES (
      v_spot_id, v_proposal.name, v_proposal.sport_type,
      v_proposal.latitude, v_proposal.longitude,
      v_proposal.address, v_proposal.surface, v_proposal.lighting,
      v_proposal.equipment, 'community'
    );
    UPDATE public.spot_proposals
    SET status = 'approved', reject_reason = NULL,
        moderator_id = p_moderator_id, reviewed_at = now()
    WHERE id = v_proposal.id;
    RETURN jsonb_build_object(
      'proposal_id', v_proposal.id, 'status', 'approved', 'spot_id', v_spot_id
    );
  END IF;

  UPDATE public.spot_proposals
  SET status = 'rejected', reject_reason = btrim(p_reason),
      moderator_id = p_moderator_id, reviewed_at = now()
  WHERE id = v_proposal.id;
  RETURN jsonb_build_object('proposal_id', v_proposal.id, 'status', 'rejected');
END;
$function$;

REVOKE ALL ON FUNCTION public.moderate_spot_proposal(uuid, uuid, text, text)
  FROM public, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.moderate_spot_proposal(uuid, uuid, text, text)
  TO service_role;

-- Secure the existing auto-RLS event trigger function against API calls.
DO $lock$
BEGIN
  IF to_regprocedure('public.rls_auto_enable()') IS NOT NULL THEN
    REVOKE ALL ON FUNCTION public.rls_auto_enable() FROM public, anon, authenticated;
  END IF;
END;
$lock$;
COMMIT;
