BEGIN;

-- Move menu.administration.teams to the PEOPLE category
UPDATE menu_items
SET section_code = 'PEOPLE',
    translation_key = 'menu.people.teams',
    display_order = 35
WHERE code = 'menu.administration.teams';

COMMIT;
