ALTER TABLE t_plugin_datatransfer_area ALTER COLUMN attachments_size RENAME TO attachments_counter;
ALTER TABLE t_plugin_datatransfer_area ADD COLUMN attachments_size BIGINT;
ALTER TABLE t_plugin_datatransfer_area ADD COLUMN observer_ids CHARACTER VARYING(4000);
