-- Transaction-local schema: never modifies an existing user or contact policy.
BEGIN;
CREATE SCHEMA ogt_imported_profile_qa;
SET LOCAL search_path=ogt_imported_profile_qa,public;
CREATE TABLE users (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), firebase_uid text UNIQUE NOT NULL,
 email text, phone_e164 text, role text NOT NULL DEFAULT 'USER',
 publishing_blocked boolean NOT NULL DEFAULT false,
 metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
 CONSTRAINT users_contact_chk CHECK(email IS NOT NULL OR phone_e164 IS NOT NULL)
);
INSERT INTO users(firebase_uid,email) VALUES('ordinary-existing','qa@example.test');
\ir ../database/migrations/42_imported_profiles.sql
DO $test$
DECLARE candidate jsonb; rejected boolean;
BEGIN
 IF (SELECT count(*) FROM users)<>1 THEN RAISE EXCEPTION 'existing contact changed'; END IF;
 candidate='{"importedProfile":{"platform":"instagram","handle":"qa.cats","sourceUrl":"https://www.instagram.com/qa.cats/"}}';
 INSERT INTO users(firebase_uid,publishing_blocked,metadata) VALUES('ogt-import:instagram:qa.cats',true,candidate);
 BEGIN
   INSERT INTO users(firebase_uid) VALUES('ordinary-without-contact');
   RAISE EXCEPTION 'ordinary contact requirement was weakened';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
   INSERT INTO users(firebase_uid,publishing_blocked,metadata) VALUES('ogt-import:instagram:qa.other',true,candidate);
   RAISE EXCEPTION 'mismatched imported identity accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
   UPDATE users SET publishing_blocked=false WHERE firebase_uid='ogt-import:instagram:qa.cats';
   RAISE EXCEPTION 'unmanaged profile gained publishing';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
   UPDATE users SET role='COMPANY_ADMIN' WHERE firebase_uid='ogt-import:instagram:qa.cats';
   RAISE EXCEPTION 'unmanaged profile gained privileged role';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
   UPDATE users SET metadata='{}' WHERE firebase_uid='ogt-import:instagram:qa.cats';
   RAISE EXCEPTION 'missing provenance accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 BEGIN
   UPDATE users SET metadata=jsonb_set(metadata,'{importedProfile,sourceUrl}','"https://example.test/qa.cats/"') WHERE firebase_uid='ogt-import:instagram:qa.cats';
   RAISE EXCEPTION 'mismatched source accepted';
 EXCEPTION WHEN check_violation THEN NULL; END;
 UPDATE users SET firebase_uid='verified-firebase-fixture',email='owner@example.test',publishing_blocked=false WHERE firebase_uid='ogt-import:instagram:qa.cats';
 IF NOT EXISTS(SELECT 1 FROM users WHERE firebase_uid='verified-firebase-fixture' AND NOT publishing_blocked) THEN RAISE EXCEPTION 'ordinary contact transition broken'; END IF;
END $test$;
SELECT 'Imported profile contact, provenance and permission checks passed; existing contacts preserved';
ROLLBACK;
