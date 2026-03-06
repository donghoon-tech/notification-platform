ALTER TABLE notification_requests 
ADD COLUMN recipient_id VARCHAR(255),
ADD COLUMN channel VARCHAR(50),
ADD COLUMN target_address VARCHAR(255);

-- Update existing records if any (though likely empty in dev)
UPDATE notification_requests SET recipient_id = 'unknown', channel = 'EMAIL' WHERE recipient_id IS NULL;

ALTER TABLE notification_requests 
ALTER COLUMN recipient_id SET NOT NULL,
ALTER COLUMN channel SET NOT NULL;
