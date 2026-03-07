ALTER TABLE notification_requests 
ADD COLUMN requested_at TIMESTAMP WITH TIME ZONE;

UPDATE notification_requests SET requested_at = created_at WHERE requested_at IS NULL;

ALTER TABLE notification_requests 
ALTER COLUMN requested_at SET NOT NULL;
