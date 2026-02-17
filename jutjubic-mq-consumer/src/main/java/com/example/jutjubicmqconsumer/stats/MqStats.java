package com.example.jutjubicmqconsumer.stats;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MqStats {
    public final AtomicLong jsonCount = new AtomicLong();
    public final AtomicLong pbCount = new AtomicLong();

    public final AtomicLong jsonDeserNanos = new AtomicLong();
    public final AtomicLong pbDeserNanos = new AtomicLong();

    public final AtomicLong jsonBytes = new AtomicLong();
    public final AtomicLong pbBytes = new AtomicLong();
}
