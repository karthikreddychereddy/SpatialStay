-- =========================================================
-- SpatialStay development fixtures
-- =========================================================


-- =========================================================
-- Hotels
-- =========================================================

INSERT INTO hotels (
    slug,
    name,
    description,
    city,
    state,
    country,
    address,
    latitude,
    longitude,
    rating,
    review_count,
    starting_price,
    hero_image_url,
    verified,
    status,
    created_at,
    updated_at
)
SELECT
    'azure-bay-resort-goa',
    'Azure Bay Resort',
    'A premium coastal stay designed for relaxed beachside experiences.',
    'Goa',
    'Goa',
    'India',
    'Calangute, Goa',
    15.5440,
    73.7555,
    4.7,
    328,
    8500.00,
    'https://images.unsplash.com/photo-1566073771259-6a8506099945',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM hotels
    WHERE slug = 'azure-bay-resort-goa'
);


INSERT INTO hotels (
    slug,
    name,
    description,
    city,
    state,
    country,
    address,
    latitude,
    longitude,
    rating,
    review_count,
    starting_price,
    hero_image_url,
    verified,
    status,
    created_at,
    updated_at
)
SELECT
    'skyline-grand-hyderabad',
    'Skyline Grand',
    'A modern city hotel offering premium accommodation in Hyderabad.',
    'Hyderabad',
    'Telangana',
    'India',
    'HITEC City, Hyderabad',
    17.4435,
    78.3772,
    4.6,
    241,
    7200.00,
    'https://images.unsplash.com/photo-1551882547-ff40c63fe5fa',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM hotels
    WHERE slug = 'skyline-grand-hyderabad'
);


INSERT INTO hotels (
    slug,
    name,
    description,
    city,
    state,
    country,
    address,
    latitude,
    longitude,
    rating,
    review_count,
    starting_price,
    hero_image_url,
    verified,
    status,
    created_at,
    updated_at
)
SELECT
    'ocean-vista-vizag',
    'Ocean Vista',
    'A sea-facing stay offering comfortable rooms near the Visakhapatnam coastline.',
    'Visakhapatnam',
    'Andhra Pradesh',
    'India',
    'Beach Road, Visakhapatnam',
    17.7144,
    83.3237,
    4.5,
    186,
    6500.00,
    'https://images.unsplash.com/photo-1564501049412-61c2a3083791',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1
    FROM hotels
    WHERE slug = 'ocean-vista-vizag'
);


-- =========================================================
-- Amenities
-- =========================================================

INSERT INTO amenities (
    code,
    name
)
VALUES
    ('WIFI', 'Wi-Fi'),
    ('AIR_CONDITIONING', 'Air Conditioning'),
    ('SMART_TV', 'Smart TV'),
    ('OCEAN_VIEW', 'Ocean View'),
    ('BALCONY', 'Balcony'),
    ('MINI_BAR', 'Mini Bar')
ON CONFLICT (code) DO NOTHING;


-- =========================================================
-- Azure Bay Resort rooms
-- =========================================================

INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'deluxe-king',
    'Deluxe King',
    'A spacious king room designed for a comfortable coastal stay.',
    'DELUXE',
    2,
    8,
    'King Bed',
    420,
    9500.00,
    'INR',
    'https://images.unsplash.com/photo-1631049307264-da0ec9d70304',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'azure-bay-resort-goa'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'ocean-suite',
    'Ocean Suite',
    'A premium suite with additional space and a coastal-view experience.',
    'SUITE',
    3,
    4,
    'King Bed',
    620,
    12500.00,
    'INR',
    'https://images.unsplash.com/photo-1618773928121-c32242e63f39',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'azure-bay-resort-goa'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'azure-bay-resort-goa'
  AND r.slug = 'deluxe-king'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'BALCONY'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'azure-bay-resort-goa'
  AND r.slug = 'ocean-suite'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'OCEAN_VIEW',
      'BALCONY',
      'MINI_BAR'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


-- =========================================================
-- Skyline Grand rooms
-- =========================================================

INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'executive-king',
    'Executive King',
    'A modern executive room designed for comfortable city stays and business travel.',
    'EXECUTIVE',
    2,
    10,
    'King Bed',
    400,
    7800.00,
    'INR',
    'https://images.unsplash.com/photo-1611892440504-42a792e24d32',
    false,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'skyline-grand-hyderabad'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'skyline-suite',
    'Skyline Suite',
    'A spacious premium suite designed for guests who want additional comfort and city views.',
    'SUITE',
    3,
    5,
    'King Bed',
    590,
    11000.00,
    'INR',
    'https://images.unsplash.com/photo-1590490360182-c33d57733427',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'skyline-grand-hyderabad'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'skyline-grand-hyderabad'
  AND r.slug = 'executive-king'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'MINI_BAR'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'skyline-grand-hyderabad'
  AND r.slug = 'skyline-suite'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'BALCONY',
      'MINI_BAR'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


-- =========================================================
-- Ocean Vista rooms
-- =========================================================

INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'sea-view-king',
    'Sea View King',
    'A comfortable king room designed around a relaxing coastal stay.',
    'DELUXE',
    2,
    7,
    'King Bed',
    410,
    7200.00,
    'INR',
    'https://images.unsplash.com/photo-1566665797739-1674de7a421a',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'ocean-vista-vizag'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO rooms (
    hotel_id,
    slug,
    name,
    description,
    room_type,
    max_guests,
    total_units,
    bed_type,
    room_size_sq_ft,
    price_per_night,
    currency,
    hero_image_url,
    spatial_available,
    status,
    created_at,
    updated_at
)
SELECT
    h.id,
    'coastal-suite',
    'Coastal Suite',
    'A larger coastal suite with additional space for couples or small families.',
    'SUITE',
    4,
    3,
    'King Bed',
    650,
    10500.00,
    'INR',
    'https://images.unsplash.com/photo-1591088398332-8a7791972843',
    true,
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM hotels h
WHERE h.slug = 'ocean-vista-vizag'
ON CONFLICT (hotel_id, slug) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'ocean-vista-vizag'
  AND r.slug = 'sea-view-king'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'OCEAN_VIEW',
      'BALCONY'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


INSERT INTO room_amenities (
    room_id,
    amenity_id
)
SELECT
    r.id,
    a.id
FROM rooms r
JOIN hotels h
    ON h.id = r.hotel_id
CROSS JOIN amenities a
WHERE h.slug = 'ocean-vista-vizag'
  AND r.slug = 'coastal-suite'
  AND a.code IN (
      'WIFI',
      'AIR_CONDITIONING',
      'SMART_TV',
      'OCEAN_VIEW',
      'BALCONY',
      'MINI_BAR'
  )
ON CONFLICT (room_id, amenity_id) DO NOTHING;


-- =========================================================
-- Keep existing development room records synchronized with
-- their Step 5 inventory capacities.
-- =========================================================

UPDATE rooms r
SET total_units = CASE
    WHEN h.slug = 'azure-bay-resort-goa'
         AND r.slug = 'deluxe-king'
        THEN 8

    WHEN h.slug = 'azure-bay-resort-goa'
         AND r.slug = 'ocean-suite'
        THEN 4

    WHEN h.slug = 'skyline-grand-hyderabad'
         AND r.slug = 'executive-king'
        THEN 10

    WHEN h.slug = 'skyline-grand-hyderabad'
         AND r.slug = 'skyline-suite'
        THEN 5

    WHEN h.slug = 'ocean-vista-vizag'
         AND r.slug = 'sea-view-king'
        THEN 7

    WHEN h.slug = 'ocean-vista-vizag'
         AND r.slug = 'coastal-suite'
        THEN 3

    ELSE r.total_units
END
FROM hotels h
WHERE r.hotel_id = h.id
  AND (
        (
            h.slug = 'azure-bay-resort-goa'
            AND r.slug IN (
                'deluxe-king',
                'ocean-suite'
            )
        )
        OR
        (
            h.slug = 'skyline-grand-hyderabad'
            AND r.slug IN (
                'executive-king',
                'skyline-suite'
            )
        )
        OR
        (
            h.slug = 'ocean-vista-vizag'
            AND r.slug IN (
                'sea-view-king',
                'coastal-suite'
            )
        )
      );