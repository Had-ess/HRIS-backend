-- The standalone leave-balance management page has been removed.
-- Balance management is now accessed via the employee profile (HR/admin click
-- an employee and open the "Manage Balance" modal). Drop the obsolete menu entry
-- and any profile/permission associations referencing it.

DELETE FROM profile_menu_access
WHERE menu_item_id IN (
    SELECT id FROM menu_items WHERE code = 'menu.administration.leaveBalances'
);

DELETE FROM menu_items
WHERE code = 'menu.administration.leaveBalances';
