-- Administrative request scenarios for employee and HR flows.

BEGIN;

INSERT INTO admin_requests (id, requester_id, request_type_id, tracking_number, description, urgency_level, status, metadata, submitted_at, resolved_at, resolved_by_id, rejection_reason)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001',
    '33333333-3333-3333-3333-333333333306',
    art.id,
    'AR-SEED-0001',
    'Requesting an employment certificate for a visa file.',
    'NORMAL',
    'SUBMITTED',
    '{"deliveryMode":"PDF","language":"FR"}',
    date_trunc('month', NOW()) + INTERVAL '3 days 09:30',
    NULL,
    NULL,
    NULL
FROM admin_request_types art
WHERE art.code = 'CERT_WORK'
ON CONFLICT (id) DO UPDATE
SET requester_id = EXCLUDED.requester_id,
    request_type_id = EXCLUDED.request_type_id,
    tracking_number = EXCLUDED.tracking_number,
    description = EXCLUDED.description,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    submitted_at = EXCLUDED.submitted_at,
    resolved_at = EXCLUDED.resolved_at,
    resolved_by_id = EXCLUDED.resolved_by_id,
    rejection_reason = EXCLUDED.rejection_reason;

INSERT INTO admin_requests (id, requester_id, request_type_id, tracking_number, description, urgency_level, status, metadata, submitted_at, resolved_at, resolved_by_id, rejection_reason)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0002',
    '33333333-3333-3333-3333-333333333307',
    art.id,
    'AR-SEED-0002',
    'Need a replacement laptop battery for frequent field visits.',
    'URGENT',
    'IN_PROGRESS',
    '{"assetCategory":"LAPTOP","requestedModel":"Latitude 7440"}',
    date_trunc('month', NOW()) + INTERVAL '4 days 14:00',
    NULL,
    NULL,
    NULL
FROM admin_request_types art
WHERE art.code = 'EQUIPMENT'
ON CONFLICT (id) DO UPDATE
SET requester_id = EXCLUDED.requester_id,
    request_type_id = EXCLUDED.request_type_id,
    tracking_number = EXCLUDED.tracking_number,
    description = EXCLUDED.description,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    submitted_at = EXCLUDED.submitted_at,
    resolved_at = EXCLUDED.resolved_at,
    resolved_by_id = EXCLUDED.resolved_by_id,
    rejection_reason = EXCLUDED.rejection_reason;

INSERT INTO admin_requests (id, requester_id, request_type_id, tracking_number, description, urgency_level, status, metadata, submitted_at, resolved_at, resolved_by_id, rejection_reason)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0003',
    '33333333-3333-3333-3333-333333333308',
    art.id,
    'AR-SEED-0003',
    'Requesting a salary certificate for the apartment rental application.',
    'NORMAL',
    'PROCESSED',
    '{"language":"EN","copies":1}',
    date_trunc('month', NOW()) + INTERVAL '2 days 11:15',
    date_trunc('month', NOW()) + INTERVAL '6 days 16:30',
    '33333333-3333-3333-3333-333333333302',
    NULL
FROM admin_request_types art
WHERE art.code = 'CERT_SALARY'
ON CONFLICT (id) DO UPDATE
SET requester_id = EXCLUDED.requester_id,
    request_type_id = EXCLUDED.request_type_id,
    tracking_number = EXCLUDED.tracking_number,
    description = EXCLUDED.description,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    submitted_at = EXCLUDED.submitted_at,
    resolved_at = EXCLUDED.resolved_at,
    resolved_by_id = EXCLUDED.resolved_by_id,
    rejection_reason = EXCLUDED.rejection_reason;

INSERT INTO admin_requests (id, requester_id, request_type_id, tracking_number, description, urgency_level, status, metadata, submitted_at, resolved_at, resolved_by_id, rejection_reason)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0004',
    '33333333-3333-3333-3333-333333333309',
    art.id,
    'AR-SEED-0004',
    'Requesting an update to emergency contact information.',
    'NORMAL',
    'REJECTED',
    '{"requestedField":"emergencyContact"}',
    date_trunc('month', NOW()) + INTERVAL '1 days 08:50',
    date_trunc('month', NOW()) + INTERVAL '2 days 15:10',
    '33333333-3333-3333-3333-333333333302',
    'Please attach the signed update form before resubmitting.'
FROM admin_request_types art
WHERE art.code = 'INFO_UPDATE'
ON CONFLICT (id) DO UPDATE
SET requester_id = EXCLUDED.requester_id,
    request_type_id = EXCLUDED.request_type_id,
    tracking_number = EXCLUDED.tracking_number,
    description = EXCLUDED.description,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    submitted_at = EXCLUDED.submitted_at,
    resolved_at = EXCLUDED.resolved_at,
    resolved_by_id = EXCLUDED.resolved_by_id,
    rejection_reason = EXCLUDED.rejection_reason;

INSERT INTO admin_requests (id, requester_id, request_type_id, tracking_number, description, urgency_level, status, metadata, submitted_at, resolved_at, resolved_by_id, rejection_reason)
SELECT
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0005',
    '33333333-3333-3333-3333-333333333306',
    art.id,
    'AR-SEED-0005',
    'Cancelled duplicate request after receiving the signed document.',
    'NORMAL',
    'CANCELLED',
    '{"channel":"self-service"}',
    date_trunc('month', NOW()) + INTERVAL '5 days 10:00',
    date_trunc('month', NOW()) + INTERVAL '5 days 12:30',
    '33333333-3333-3333-3333-333333333306',
    NULL
FROM admin_request_types art
WHERE art.code = 'OTHER'
ON CONFLICT (id) DO UPDATE
SET requester_id = EXCLUDED.requester_id,
    request_type_id = EXCLUDED.request_type_id,
    tracking_number = EXCLUDED.tracking_number,
    description = EXCLUDED.description,
    urgency_level = EXCLUDED.urgency_level,
    status = EXCLUDED.status,
    metadata = EXCLUDED.metadata,
    submitted_at = EXCLUDED.submitted_at,
    resolved_at = EXCLUDED.resolved_at,
    resolved_by_id = EXCLUDED.resolved_by_id,
    rejection_reason = EXCLUDED.rejection_reason;

COMMIT;
