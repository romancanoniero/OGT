-- Integration smoke for OGT user restriction and deletion migrations.
-- Runs only against an isolated disposable PostgreSQL + PostGIS service.
\set ON_ERROR_STOP on
BEGIN;
INSERT INTO users(id, firebase_uid, email, display_name, invite_code)
VALUES
 ('88888888-8888-4888-8888-888888888881','qa-survivor','survivor@qa.example','Survivor','QA-SURVIVOR'),
 ('88888888-8888-4888-8888-888888888882','qa-target','target@qa.example','Target','QA-TARGET');

INSERT INTO social_posts(id, author_kind, author_user_id, body)
VALUES
 ('88888888-8888-4888-8888-888888888891','USER','88888888-8888-4888-8888-888888888881','Remaining post'),
 ('88888888-8888-4888-8888-888888888892','USER','88888888-8888-4888-8888-888888888882','Deleted post');

INSERT INTO post_comments(id, post_id, author_user_id, body)
VALUES ('88888888-8888-4888-8888-888888888893',
 '88888888-8888-4888-8888-888888888891','88888888-8888-4888-8888-888888888882','Deleted comment');

INSERT INTO post_impacts(post_id, user_id)
VALUES ('88888888-8888-4888-8888-888888888891','88888888-8888-4888-8888-888888888882');

INSERT INTO post_anecdotes(id,post_id,author_user_id,body)
VALUES ('88888888-8888-4888-8888-888888888894',
 '88888888-8888-4888-8888-888888888891','88888888-8888-4888-8888-888888888882','Deleted anecdote');

INSERT INTO content_reports(reporter_id, post_id, reason)
VALUES ('88888888-8888-4888-8888-888888888882',
 '88888888-8888-4888-8888-888888888891','Report retained without erased reporter');

INSERT INTO crm_audit_events(actor_user_id, action, target_type, target_id, reason, outcome)
VALUES ('88888888-8888-4888-8888-888888888882','USER_PROFILE_VIEW','user',
 '88888888-8888-4888-8888-888888888882','Test audit','SUCCESS');

UPDATE users SET publishing_blocked=TRUE
 WHERE id = '88888888-8888-4888-8888-888888888881';
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM users WHERE firebase_uid='qa-survivor'
   AND status='ACTIVE' AND publishing_blocked) THEN
  RAISE EXCEPTION 'restriction unexpectedly disables reading or fails to persist';
 END IF;
END $$;

INSERT INTO crm_erased_accounts(uid_hash,actor_user_id,reason)
VALUES (encode(digest('qa-target','sha256'),'hex'),
 '88888888-8888-4888-8888-888888888881','Test deletion with moderation audit');

DELETE FROM social_posts WHERE author_user_id='88888888-8888-4888-8888-888888888882';
DELETE FROM users WHERE id='88888888-8888-4888-8888-888888888882';
DO $$
BEGIN
 IF EXISTS(SELECT 1 FROM users WHERE firebase_uid='qa-target') THEN
  RAISE EXCEPTION 'erased user survived';
 END IF;
 IF EXISTS(SELECT 1 FROM social_posts WHERE id='88888888-8888-4888-8888-888888888892') THEN
  RAISE EXCEPTION 'erased post survived';
 END IF;
 IF EXISTS(SELECT 1 FROM post_comments WHERE id='88888888-8888-4888-8888-888888888893') THEN
  RAISE EXCEPTION 'erased comment survived';
 END IF;
 IF EXISTS(SELECT 1 FROM post_impacts WHERE user_id='88888888-8888-4888-8888-888888888882') THEN
  RAISE EXCEPTION 'erased impact survived';
 END IF;
 IF EXISTS(SELECT 1 FROM post_anecdotes WHERE id='88888888-8888-4888-8888-888888888894') THEN
  RAISE EXCEPTION 'erased anecdote survived';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM content_reports
               WHERE reporter_id IS NULL AND reason='Report retained without erased reporter') THEN
  RAISE EXCEPTION 'anonymous moderation evidence lost';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM crm_audit_events
               WHERE actor_user_id IS NULL AND action='USER_PROFILE_VIEW') THEN
  RAISE EXCEPTION 'actor FK nulling or audit retention broken';
 END IF;
 IF NOT EXISTS(SELECT 1 FROM crm_erased_accounts
               WHERE uid_hash=encode(digest('qa-target','sha256'),'hex')) THEN
  RAISE EXCEPTION 'erased Firebase UID was not tombstoned';
 END IF;
END $$;
-- Immutable audit cannot be rewritten, except the FK's privacy-required nulling.
DO $$
DECLARE changed boolean := FALSE;
BEGIN
 BEGIN
   UPDATE crm_audit_events SET action = 'FORGED'
    WHERE action = 'USER_PROFILE_VIEW';
 EXCEPTION WHEN raise_exception THEN
   changed := TRUE;
 END;
 IF NOT changed THEN RAISE EXCEPTION 'audit immutability was bypassed'; END IF;
END $$;
ROLLBACK;
