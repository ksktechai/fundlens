-- Sample funds (fictional but realistic metrics) so the API is usable out of
-- the box. IDs are identity-generated; metrics reference funds by name.

INSERT INTO funds (name, provider, status, classification, risk_indicator, description)
VALUES
    ('Westpac Active Growth', 'Westpac', 'open', 'Growth', 4,
     'An actively managed growth fund investing mainly in equities with some fixed interest.'),
    ('Milford Active Growth', 'Milford', 'open', 'Growth', 4,
     'An actively managed fund targeting growth assets, primarily Australasian and global equities.'),
    ('Simplicity Growth', 'Simplicity', 'open', 'Growth', 4,
     'A low-fee, passively managed growth fund tracking diversified global indices.');

INSERT INTO fund_metrics (fund_id, period_end, total_annual_fund_charge, managers_basic_fee,
                          performance_based_fees, contribution_fee, withdrawal_fee,
                          past_year_return_net, avg_five_year_return_net,
                          market_index_past_year_return, total_fund_value, number_of_investors,
                          investment_mix, top_ten_investments)
VALUES
    ((SELECT id FROM funds WHERE name = 'Westpac Active Growth'), DATE '2026-03-31',
     1.05, 0.95, 0.00, 0.00, 0.00, -4.10, 6.20, -3.50, 2150000000.00, 98000,
     '[{"investmentType":"International equities","targetPercentageOrRange":"55%","actualPercentage":54.2},{"investmentType":"Australasian equities","targetPercentageOrRange":"20%","actualPercentage":21.1},{"investmentType":"Fixed interest","targetPercentageOrRange":"20%","actualPercentage":19.5},{"investmentType":"Cash","targetPercentageOrRange":"5%","actualPercentage":5.2}]',
     '[{"assetName":"Microsoft Corp","assetProportion":2.10,"assetType":"International equity","assetCountry":"US"},{"assetName":"Apple Inc","assetProportion":1.90,"assetType":"International equity","assetCountry":"US"}]'),
    ((SELECT id FROM funds WHERE name = 'Milford Active Growth'), DATE '2026-03-31',
     1.15, 1.05, 0.10, 0.00, 0.00, 2.80, 9.10, 1.90, 3400000000.00, 72000,
     '[{"investmentType":"International equities","targetPercentageOrRange":"40-60%","actualPercentage":48.7},{"investmentType":"Australasian equities","targetPercentageOrRange":"25%","actualPercentage":26.3},{"investmentType":"Fixed interest","targetPercentageOrRange":"15%","actualPercentage":16.0},{"investmentType":"Cash","targetPercentageOrRange":"10%","actualPercentage":9.0}]',
     '[{"assetName":"Fisher & Paykel Healthcare","assetProportion":3.20,"assetType":"Australasian equity","assetCountry":"NZ"},{"assetName":"Contact Energy","assetProportion":2.40,"assetType":"Australasian equity","assetCountry":"NZ"}]'),
    ((SELECT id FROM funds WHERE name = 'Simplicity Growth'), DATE '2026-03-31',
     0.29, 0.29, 0.00, 0.00, 0.00, 1.50, 7.80, 1.90, 1900000000.00, 65000,
     '[{"investmentType":"International equities","targetPercentageOrRange":"63%","actualPercentage":62.8},{"investmentType":"Australasian equities","targetPercentageOrRange":"15%","actualPercentage":15.1},{"investmentType":"Fixed interest","targetPercentageOrRange":"17%","actualPercentage":17.0},{"investmentType":"Cash","targetPercentageOrRange":"5%","actualPercentage":5.1}]',
     '[{"assetName":"Vanguard Intl Shares Index","assetProportion":28.50,"assetType":"International equity fund","assetCountry":"AU"},{"assetName":"NZX 50 Index Fund","assetProportion":9.80,"assetType":"Australasian equity fund","assetCountry":"NZ"}]');
