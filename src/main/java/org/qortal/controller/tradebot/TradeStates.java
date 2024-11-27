package org.qortal.controller.tradebot;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TradeStates {
    public enum State implements TradeBot.StateNameAndValueSupplier {
        BOB_WAITING_FOR_AT_CONFIRM(10, false, false),
        BOB_WAITING_FOR_MESSAGE(15, true, true),
        BOB_WAITING_FOR_AT_REDEEM(25, true, true),
        BOB_DONE(30, false, false),
        BOB_REFUNDED(35, false, false),

        ALICE_WAITING_FOR_AT_LOCK(85, true, true),
        ALICE_DONE(95, false, false),
        ALICE_REFUNDING_A(105, true, true),
        ALICE_REFUNDED(110, false, false);

        private static final Map<Integer, State> map = Stream.of(State.values())
                .collect(Collectors.toUnmodifiableMap(state -> state.value, state -> state));

        public final int value;
        public final boolean requiresAtData;
        public final boolean requiresTradeData;

        // Constructor
        State(int value, boolean requiresAtData, boolean requiresTradeData) {
            this.value = value;
            this.requiresAtData = requiresAtData;
            this.requiresTradeData = requiresTradeData;
        }

        /**
         * Retrieve State by value.
         * Throws an exception if the value is invalid.
         *
         * @param value the integer value representing the state
         * @return the corresponding State
         * @throws IllegalArgumentException if the value is invalid
         */
        
        public static State fromValue(int value) {
            return Optional.ofNullable(map.get(value))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid state value: " + value));
        }

        /**
         * Safely retrieves the State by value without throwing an exception.
         *
         * @param value the integer value representing the state
         * @return an Optional containing the corresponding State, or empty if invalid
         */
        
        public static Optional<State> safeFromValue(int value) {
            return Optional.ofNullable(map.get(value));
        }

        @Override
        public String getState() {
            return this.name();
        }

        @Override
        public int getStateValue() {
            return this.value;
        }
    }
}
