-- Only web trimming may receive a temporary source over 8 MiB. Stored uploads stay capped at 8 MiB.
ALTER TABLE media_processing_metrics DROP CONSTRAINT IF EXISTS media_processing_metrics_received_bytes_check;
ALTER TABLE media_processing_metrics ADD CONSTRAINT media_processing_metrics_received_bytes_check
CHECK(received_bytes BETWEEN 1 AND 12582912);
