-- Demo accounts and an opening funding entry, so a fresh deployment has
-- something to transfer. Balances are derived, so the funding is posted as a
-- proper balanced entry rather than seeded as a number.

INSERT INTO accounts (id, account_number, holder_name, type, status, currency, overdraft_limit_minor)
VALUES
    ('11111111-1111-4111-8111-111111111111', 'AE070331234567890123456', 'Amina Haddad',        'CUSTOMER', 'ACTIVE', 'AED', 0),
    ('22222222-2222-4222-8222-222222222222', 'AE070331234567890123457', 'Rafael Santos',       'CUSTOMER', 'ACTIVE', 'AED', 50000),
    ('33333333-3333-4333-8333-333333333333', 'AE070331234567890123458', 'Lina Meyer',          'CUSTOMER', 'FROZEN', 'AED', 0),
    ('44444444-4444-4444-8444-444444444444', 'NOSTRO-AED-001',          'Correspondent nostro','NOSTRO',   'ACTIVE', 'AED', 0),
    ('55555555-5555-4555-8555-555555555555', 'SUSPENSE-AED-001',        'Payments suspense',   'SUSPENSE', 'ACTIVE', 'AED', 0),
    ('66666666-6666-4666-8666-666666666666', 'INCOME-FEES-AED',         'Fee income',          'INCOME',   'ACTIVE', 'AED', 0);

INSERT INTO journal_entries (id, reference, narrative)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'OPENING-001', 'Opening funding from correspondent');

-- Debit the nostro (the asset grows), credit the customers (liabilities grow).
INSERT INTO postings (journal_entry_id, account_id, direction, amount_minor, currency)
VALUES
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '44444444-4444-4444-8444-444444444444', 'DEBIT',  1500000, 'AED'),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '11111111-1111-4111-8111-111111111111', 'CREDIT', 1000000, 'AED'),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '22222222-2222-4222-8222-222222222222', 'CREDIT',  350000, 'AED'),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '33333333-3333-4333-8333-333333333333', 'CREDIT',  150000, 'AED');
