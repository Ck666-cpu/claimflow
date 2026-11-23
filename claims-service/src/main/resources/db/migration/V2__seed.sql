-- Demo data. Fixed UUIDs so the frontend role switcher and README can reference them.
-- Seeded claims bypass the API, so they produce no events; only new actions do.

INSERT INTO markets (code, name, currency) VALUES
    ('SG', 'Singapore', 'SGD'),
    ('MY', 'Malaysia',  'MYR'),
    ('HK', 'Hong Kong', 'HKD'),
    ('AU', 'Australia', 'AUD'),
    ('TH', 'Thailand',  'THB'),
    ('ID', 'Indonesia', 'IDR');

INSERT INTO users (id, name, email, role, team) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Alice Tan',    'alice@example.com',  'CLAIMANT', NULL),
    ('00000000-0000-0000-0000-000000000002', 'Raj Kumar',    'raj@example.com',    'CLAIMANT', NULL),
    ('00000000-0000-0000-0000-000000000011', 'Ben Lim',      'ben@example.com',    'OFFICER',  'APAC-1'),
    ('00000000-0000-0000-0000-000000000012', 'Chitra Nair',  'chitra@example.com', 'OFFICER',  'APAC-1'),
    ('00000000-0000-0000-0000-000000000021', 'Daniel Wong',  'daniel@example.com', 'LEAD',     'APAC-1');

-- Claims across statuses, markets and ages (ages relative to migration time)
INSERT INTO claims (id, reference_no, type, market_code, currency, policy_number, incident_date, description,
                    claimed_amount, reserve_amount, approved_amount, status, rejection_reason,
                    claimant_id, assignee_id, submitted_at, decided_at, settled_at, updated_at) VALUES
    ('10000000-0000-0000-0000-000000000001', 'CLM-000001', 'MOTOR', 'SG', 'SGD', 'POL-SG-1001', CURRENT_DATE - 3,
     'Rear-ended at traffic light on PIE, bumper and tail light damaged.',
     2800.00, 2800.00, NULL, 'SUBMITTED', NULL,
     '00000000-0000-0000-0000-000000000001', NULL, now() - interval '2 days', NULL, NULL, now() - interval '2 days'),

    ('10000000-0000-0000-0000-000000000002', 'CLM-000002', 'PROPERTY', 'MY', 'MYR', 'POL-MY-2001', CURRENT_DATE - 14,
     'Burst pipe flooded kitchen, cabinets and flooring damaged.',
     15000.00, 15000.00, NULL, 'SUBMITTED', NULL,
     '00000000-0000-0000-0000-000000000002', NULL, now() - interval '12 days', NULL, NULL, now() - interval '12 days'),

    ('10000000-0000-0000-0000-000000000003', 'CLM-000003', 'MOTOR', 'HK', 'HKD', 'POL-HK-3001', CURRENT_DATE - 9,
     'Side mirror and door scraped in car park.',
     6500.00, 5200.00, NULL, 'UNDER_REVIEW', NULL,
     '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000011',
     now() - interval '8 days', NULL, NULL, now() - interval '5 days'),

    ('10000000-0000-0000-0000-000000000004', 'CLM-000004', 'PROPERTY', 'SG', 'SGD', 'POL-SG-1002', CURRENT_DATE - 20,
     'Ceiling leak from upstairs unit damaged bedroom.',
     9000.00, 7500.00, NULL, 'INFO_REQUESTED', NULL,
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000012',
     now() - interval '18 days', NULL, NULL, now() - interval '4 days'),

    ('10000000-0000-0000-0000-000000000005', 'CLM-000005', 'MOTOR', 'MY', 'MYR', 'POL-MY-2002', CURRENT_DATE - 30,
     'Windscreen cracked by debris on highway.',
     1200.00, 1100.00, 1100.00, 'APPROVED', NULL,
     '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000011',
     now() - interval '25 days', now() - interval '3 days', NULL, now() - interval '3 days'),

    ('10000000-0000-0000-0000-000000000006', 'CLM-000006', 'MOTOR', 'AU', 'AUD', 'POL-AU-4001', CURRENT_DATE - 45,
     'Hail damage to bonnet and roof.',
     4300.00, 0.00, 4000.00, 'SETTLED', NULL,
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000012',
     now() - interval '40 days', now() - interval '20 days', now() - interval '15 days', now() - interval '15 days'),

    ('10000000-0000-0000-0000-000000000007', 'CLM-000007', 'PROPERTY', 'TH', 'THB', 'POL-TH-5001', CURRENT_DATE - 35,
     'Laptop stolen from home, no sign of forced entry.',
     45000.00, 0.00, NULL, 'REJECTED', 'Theft without forced entry is excluded under section 4.2.',
     '00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000011',
     now() - interval '30 days', now() - interval '22 days', NULL, now() - interval '22 days');

INSERT INTO information_requests (id, claim_id, question, requested_by, requested_at, status) VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000004',
     'Please upload photos of the ceiling and the plumber''s report.',
     '00000000-0000-0000-0000-000000000012', now() - interval '4 days', 'OPEN');

INSERT INTO claim_status_history (claim_id, from_status, to_status, actor_id, reason, occurred_at) VALUES
    ('10000000-0000-0000-0000-000000000003', NULL, 'SUBMITTED', '00000000-0000-0000-0000-000000000001', NULL, now() - interval '8 days'),
    ('10000000-0000-0000-0000-000000000003', 'SUBMITTED', 'UNDER_REVIEW', '00000000-0000-0000-0000-000000000011', NULL, now() - interval '5 days'),
    ('10000000-0000-0000-0000-000000000004', NULL, 'SUBMITTED', '00000000-0000-0000-0000-000000000002', NULL, now() - interval '18 days'),
    ('10000000-0000-0000-0000-000000000004', 'SUBMITTED', 'UNDER_REVIEW', '00000000-0000-0000-0000-000000000012', NULL, now() - interval '10 days'),
    ('10000000-0000-0000-0000-000000000004', 'UNDER_REVIEW', 'INFO_REQUESTED', '00000000-0000-0000-0000-000000000012', 'Photos and plumber report needed', now() - interval '4 days');
