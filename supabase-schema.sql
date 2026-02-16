-- Strategy Executions Table
-- Stores results from web UI strategy executions
-- Run this SQL in Supabase SQL Editor

CREATE TABLE IF NOT EXISTS strategy_executions (
    id BIGSERIAL PRIMARY KEY,
    execution_id TEXT UNIQUE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    strategy_ids TEXT[] NOT NULL,
    results JSONB NOT NULL,
    total_trades_found INTEGER DEFAULT 0,
    execution_time_ms INTEGER DEFAULT 0,
    telegram_sent BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Index for fast retrieval of latest results
CREATE INDEX IF NOT EXISTS idx_strategy_executions_executed_at 
ON strategy_executions(executed_at DESC);

-- Index for searching by execution_id
CREATE INDEX IF NOT EXISTS idx_strategy_executions_execution_id 
ON strategy_executions(execution_id);

-- Comments for documentation
COMMENT ON TABLE strategy_executions IS 'Stores trading strategy execution results from web UI';
COMMENT ON COLUMN strategy_executions.execution_id IS 'Unique identifier for each execution (format: exec_<timestamp>)';
COMMENT ON COLUMN strategy_executions.strategy_ids IS 'Array of strategy IDs that were executed';
COMMENT ON COLUMN strategy_executions.results IS 'JSONB containing detailed results for each strategy';
COMMENT ON COLUMN strategy_executions.total_trades_found IS 'Total number of trades found across all strategies';
COMMENT ON COLUMN strategy_executions.execution_time_ms IS 'Total execution time in milliseconds';
COMMENT ON COLUMN strategy_executions.telegram_sent IS 'Whether Telegram notifications were sent';
