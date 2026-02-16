-- Per-Strategy Result Storage
-- Stores the latest result for each strategy independently

CREATE TABLE IF NOT EXISTS latest_strategy_results (
    strategy_id TEXT PRIMARY KEY,
    strategy_name TEXT NOT NULL,
    execution_time_ms BIGINT NOT NULL,
    trades_found INTEGER NOT NULL,
    trades JSONB NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for faster queries by update time
CREATE INDEX IF NOT EXISTS idx_latest_strategy_results_updated_at 
ON latest_strategy_results(updated_at DESC);

-- Enable Row Level Security (optional, for multi-tenant scenarios)
ALTER TABLE latest_strategy_results ENABLE ROW LEVEL SECURITY;

-- Policy to allow all operations (adjust based on your security needs)
CREATE POLICY "Allow all operations on latest_strategy_results" 
ON latest_strategy_results 
FOR ALL 
USING (true);
