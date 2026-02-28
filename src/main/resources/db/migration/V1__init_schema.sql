-- V1__init_schema.sql

-- 1. notification_requests (알림 요청 원본)
-- 외부 서비스가 우리에게 알림 발송을 "요청"한 기록 (단건/다건 요청의 최상위 부모)
CREATE TABLE notification_requests (
    id UUID PRIMARY KEY, -- Snowflake ID or UUIDv7
    idempotency_key VARCHAR(100) UNIQUE, -- 중복 요청 방지용 (FR-11)
    producer_name VARCHAR(50) NOT NULL, -- 발송 요청한 서비스명 (예: "ORDER_SERVICE")
    priority VARCHAR(20) NOT NULL, -- HIGH, NORMAL (FR-05)
    payload JSONB, -- 템플릿에 치환될 원본 데이터 ({"orderId": "123", "amount": 5000})
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 2. delivery_logs (실제 채널별 발송 기록)
-- 하나의 Request가 여러 채널(Email, In-App)로 찢어져서 발송될 때 각각의 상태를 추적 (FR-24)
CREATE TABLE delivery_logs (
    id UUID PRIMARY KEY,
    request_id UUID REFERENCES notification_requests(id), -- 어떤 요청에서 파생되었는가
    recipient_id VARCHAR(100) NOT NULL, -- 유저 ID
    channel VARCHAR(20) NOT NULL, -- EMAIL, IN_APP
    target_address VARCHAR(255), -- 실제 발송 목적지 (east@example.com 또는 device-token)
    status VARCHAR(20) NOT NULL, -- PENDING -> QUEUED -> DISPATCHED -> DELIVERED -> FAILED
    error_message TEXT, -- 실패 시 에러 사유 기록
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexing 전략 (조회 및 파티셔닝 대비)
CREATE INDEX idx_delivery_logs_recipient_channel ON delivery_logs(recipient_id, channel);
CREATE INDEX idx_delivery_logs_status_created ON delivery_logs(status, created_at);
