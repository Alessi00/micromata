
ALTER TABLE t_employee_vacation_remaining rename TO t_employee_remaining_leave;

ALTER TABLE t_employee_remaining_leave ALTER COLUMN carry_vacation_days_from_previous_year RENAME TO remaining_from_previous_year;

ALTER TABLE t_employee_vacation ALTER COLUMN is_half_day RENAME TO is_half_day_begin;
