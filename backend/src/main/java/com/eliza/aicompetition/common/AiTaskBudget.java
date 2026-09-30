package com.eliza.aicompetition.common;

import java.time.LocalDateTime;

/** Deadline inherited by nested extraction/model calls on one task worker thread. */
public final class AiTaskBudget {
    private static final ThreadLocal<LocalDateTime> DEADLINE = new ThreadLocal<>();
    private static final ThreadLocal<Long> TASK_ID = new ThreadLocal<>();

    private AiTaskBudget() {}

    public static void set(LocalDateTime deadline) { DEADLINE.set(deadline); }
    public static void set(LocalDateTime deadline, Long taskId) { DEADLINE.set(deadline); TASK_ID.set(taskId); }
    public static void clear() { DEADLINE.remove(); TASK_ID.remove(); }
    public static LocalDateTime deadline() { return DEADLINE.get(); }
    public static Long taskId() { return TASK_ID.get(); }
    public static long remainingMillis() {
        LocalDateTime deadline = DEADLINE.get();
        return deadline == null ? Long.MAX_VALUE : java.time.Duration.between(LocalDateTime.now(), deadline).toMillis();
    }
    public static boolean exhausted() { return remainingMillis() <= 0; }
}
