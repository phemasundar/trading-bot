package com.hemasundar.options.expectedmove;

import com.hemasundar.options.models.OptionChainResponse.OptionData;

/**
 * Paired Call and Put option contracts at a single strike price for a single expiration date.
 *
 * @param strike strike price in dollars
 * @param call   call option contract at this strike, or {@code null} if absent
 * @param put    put option contract at this strike, or {@code null} if absent
 */
public record StrikeRow(double strike, OptionData call, OptionData put) {
}
