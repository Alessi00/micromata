
ALTER TABLE t_employee_vacation_remaining rename TO t_employee_remaining_leave;

ALTER TABLE t_employee_remaining_leave RENAME carry_vacation_days_from_previous_year TO remaining_from_previous_year;

ALTER TABLE t_employee_vacation RENAME is_half_day TO is_half_day_begin;
