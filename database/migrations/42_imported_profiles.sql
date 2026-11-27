-- Internal, clearly attributed profiles can exist before their owner joins OGT.
-- Ordinary identities retain the existing contact requirement. This
-- exception grants no login credential, role, consent or publishing permission.
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_contact_chk;
ALTER TABLE users ADD CONSTRAINT users_contact_chk CHECK (
    email IS NOT NULL OR phone_e164 IS NOT NULL OR COALESCE((
        role = 'USER' AND publishing_blocked
        AND metadata->'importedProfile'->>'platform' = 'instagram'
        AND metadata->'importedProfile'->>'handle' ~ '^[A-Za-z0-9_.]{1,30}$'
        AND firebase_uid = 'ogt-import:instagram:' || (metadata->'importedProfile'->>'handle')
        AND metadata->'importedProfile'->>'sourceUrl' =
            'https://www.instagram.com/' || (metadata->'importedProfile'->>'handle') || '/'
    ), FALSE)
);
