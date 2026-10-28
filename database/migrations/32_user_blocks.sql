-- Personal safety controls are independent of administrative publication restrictions.
CREATE TABLE IF NOT EXISTS user_blocks (
    blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY(blocker_id,blocked_id),
    CHECK(blocker_id<>blocked_id)
);
CREATE INDEX IF NOT EXISTS user_blocks_reverse ON user_blocks(blocked_id,blocker_id);
CREATE OR REPLACE FUNCTION ogt_users_can_interact(a UUID,b UUID) RETURNS BOOLEAN
LANGUAGE SQL STABLE AS $$
 SELECT a=b OR b IS NULL OR NOT EXISTS(
   SELECT 1 FROM user_blocks WHERE (blocker_id=a AND blocked_id=b) OR (blocker_id=b AND blocked_id=a)
 )
$$;
-- Serialize block and direct-contact writes for the same pair, including requests already in flight.
CREATE OR REPLACE FUNCTION ogt_lock_user_pair(a UUID,b UUID) RETURNS VOID
LANGUAGE SQL VOLATILE AS $$ SELECT pg_advisory_xact_lock(hashtextextended('user-contact:'||least(a,b)::text||':'||greatest(a,b)::text,0)) $$;
CREATE OR REPLACE FUNCTION ogt_check_block_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 PERFORM ogt_lock_user_pair(NEW.blocker_id,NEW.blocked_id);
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS user_block_pair_lock ON user_blocks;
CREATE TRIGGER user_block_pair_lock BEFORE INSERT ON user_blocks FOR EACH ROW EXECUTE FUNCTION ogt_check_block_pair();
CREATE OR REPLACE FUNCTION ogt_check_message_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE a UUID; b UUID;
BEGIN
 SELECT requester_id,provider_id INTO a,b FROM timebank_matches WHERE id=NEW.match_id;
 IF a IS NULL OR NEW.sender_id NOT IN(a,b) THEN RAISE EXCEPTION 'Hilo no disponible' USING ERRCODE='23514'; END IF;
 PERFORM ogt_lock_user_pair(a,b);
 IF NOT ogt_users_can_interact(a,b) THEN RAISE EXCEPTION 'Hilo no disponible' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS timebank_message_block_guard ON timebank_messages;
CREATE TRIGGER timebank_message_block_guard BEFORE INSERT ON timebank_messages FOR EACH ROW EXECUTE FUNCTION ogt_check_message_pair();

CREATE OR REPLACE FUNCTION ogt_check_follow_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 PERFORM ogt_lock_user_pair(NEW.follower_id,NEW.followed_id);
 IF NOT ogt_users_can_interact(NEW.follower_id,NEW.followed_id) THEN RAISE EXCEPTION 'Interacción no disponible' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS follows_block_guard ON follows;
CREATE TRIGGER follows_block_guard BEFORE INSERT ON follows FOR EACH ROW EXECUTE FUNCTION ogt_check_follow_pair();

CREATE OR REPLACE FUNCTION ogt_check_match_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 PERFORM ogt_lock_user_pair(NEW.requester_id,NEW.provider_id);
 IF NOT ogt_users_can_interact(NEW.requester_id,NEW.provider_id) THEN RAISE EXCEPTION 'Interacción no disponible' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS timebank_match_block_guard ON timebank_matches;
CREATE TRIGGER timebank_match_block_guard BEFORE INSERT ON timebank_matches FOR EACH ROW EXECUTE FUNCTION ogt_check_match_pair();

CREATE OR REPLACE FUNCTION ogt_check_need_support_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE owner_id UUID;
BEGIN
 SELECT user_id INTO owner_id FROM timebank_needs WHERE id=NEW.need_id;
 PERFORM ogt_lock_user_pair(owner_id,NEW.user_id);
 IF NOT ogt_users_can_interact(owner_id,NEW.user_id) THEN RAISE EXCEPTION 'Interacción no disponible' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS timebank_support_block_guard ON timebank_need_supports;
CREATE TRIGGER timebank_support_block_guard BEFORE INSERT ON timebank_need_supports FOR EACH ROW EXECUTE FUNCTION ogt_check_need_support_pair();

CREATE OR REPLACE FUNCTION ogt_check_need_invite_pair() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
 PERFORM ogt_lock_user_pair(NEW.inviter_id,NEW.invitee_id);
 IF NOT ogt_users_can_interact(NEW.inviter_id,NEW.invitee_id) THEN RAISE EXCEPTION 'Interacción no disponible' USING ERRCODE='23514'; END IF;
 RETURN NEW;
END $$;
DROP TRIGGER IF EXISTS timebank_invite_block_guard ON timebank_need_invites;
CREATE TRIGGER timebank_invite_block_guard BEFORE INSERT OR UPDATE ON timebank_need_invites FOR EACH ROW EXECUTE FUNCTION ogt_check_need_invite_pair();
