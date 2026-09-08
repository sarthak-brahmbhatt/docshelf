// Shared status enum for DB-backed job tables (ingestion_job, portfolio_job)
package com.docshelf.common;

public enum JobStatus { QUEUED, RUNNING, DONE, FAILED }
