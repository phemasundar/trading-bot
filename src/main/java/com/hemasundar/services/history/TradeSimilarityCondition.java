package com.hemasundar.services.history;

import com.hemasundar.dto.Trade;
import com.hemasundar.dto.TradeLegDTO;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Interface and default implementation for dynamic similar trade matching logic.
 */
@Component
public class TradeSimilarityCondition {

    /**
     * Determines whether a historical trade candidate is similar to a target trade.
     *
     * @param target    Current trade being evaluated
     * @param candidate Historical trade candidate
     * @return true if trades are structurally and functionally similar, false otherwise
     */
    public boolean isSimilar(Trade target, Trade candidate) {
        if (target == null || candidate == null) {
            return false;
        }

        // 1. Ticker equality check
        if (target.getSymbol() == null || !target.getSymbol().equalsIgnoreCase(candidate.getSymbol())) {
            return false;
        }

        // 2. Expiry date equality check
        if (!equalsIgnoreNull(target.getExpiryDate(), candidate.getExpiryDate())) {
            return false;
        }

        List<TradeLegDTO> targetLegs = target.getLegs();
        List<TradeLegDTO> candidateLegs = candidate.getLegs();

        // 3. Leg count check
        if (targetLegs == null || candidateLegs == null || targetLegs.size() != candidateLegs.size()) {
            return false;
        }

        // 3. Leg structures check (Action, OptionType, Quantity)
        for (int i = 0; i < targetLegs.size(); i++) {
            TradeLegDTO tLeg = targetLegs.get(i);
            TradeLegDTO cLeg = candidateLegs.get(i);

            if (tLeg == null || cLeg == null) {
                return false;
            }

            if (!equalsIgnoreNull(tLeg.getAction(), cLeg.getAction()) ||
                !equalsIgnoreNull(tLeg.getOptionType(), cLeg.getOptionType()) ||
                tLeg.getQuantity() != cLeg.getQuantity()) {
                return false;
            }
        }

        return true;
    }

    private boolean equalsIgnoreNull(String s1, String s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;
        return s1.equalsIgnoreCase(s2);
    }
}
