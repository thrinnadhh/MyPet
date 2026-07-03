INSERT INTO content.promo_banners (title, subtitle, accent_color, duration_sec, sort_order, active) VALUES
('Free delivery today', 'On orders above ₹499 from nearby stores', '#F97316', 5, 1, true),
('Grooming week', 'Book a spa slot and get 10% off', '#2563EB', 4, 2, true),
('Vet checkup drive', 'Annual wellness packages from ₹799', '#14B8A6', 3, 3, true),
('New puppy guide', 'Read age-wise care tips in Guides', '#B45309', 2, 4, true),
('Tick season alert', 'Prevention tips for monsoon months', '#B91C1C', 1, 5, true)
ON CONFLICT DO NOTHING;

INSERT INTO content.guide_articles (category, title, summary, read_minutes) VALUES
('puppy-kitten', 'First 8 weeks at home', 'Set a feeding routine and safe sleep zone.', 4),
('puppy-kitten', 'Core vaccines timeline', 'DHPP and rabies schedule for puppies.', 5),
('skin', 'Itchy skin checklist', 'Food, fleas, or allergies — what to check first.', 3),
('skin', 'When to book a vet', 'Red flags that need same-day attention.', 2),
('ticks-odor', 'Tick prevention 101', 'Spot-on, collars, and yard hygiene.', 4),
('ticks-odor', 'Managing pet odor', 'Bath frequency and ear cleaning tips.', 3);
