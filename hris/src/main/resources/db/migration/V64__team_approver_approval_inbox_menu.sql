-- Grant the Approvals Inbox menu item to TEAM_APPROVER_PROFILE and DEPT_APPROVER_PROFILE
-- so that users with these profiles see the /approval-inbox link in the sidebar.
INSERT INTO profile_menu_access (profile_id, menu_item_id)
SELECT p.id, m.id
FROM access_profiles p
CROSS JOIN menu_items m
WHERE p.code IN ('TEAM_APPROVER_PROFILE', 'DEPT_APPROVER_PROFILE')
  AND m.route = '/approval-inbox'
ON CONFLICT (profile_id, menu_item_id) DO NOTHING;
