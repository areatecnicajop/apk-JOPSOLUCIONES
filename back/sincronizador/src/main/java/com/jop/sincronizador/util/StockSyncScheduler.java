package com.jop.sincronizador.util;

import com.jop.sincronizador.services.IRestLaudusWordP;
import com.jop.sincronizador.webSockets.WebSocket;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class StockSyncScheduler {

    @Autowired
    private IRestLaudusWordP restLaudusWordP;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> task;

    private boolean enabled = false;
    private int intervalMinutes = 60;
    private Instant lastRun;
    private Instant nextRun;

    public synchronized void start(int minutes) {
        stop();
        this.intervalMinutes = minutes;
        this.enabled = true;
        this.nextRun = Instant.now().plusSeconds(minutes * 60L);
        task = executor.scheduleAtFixedRate(this::runSync, minutes, minutes, TimeUnit.MINUTES);
        WebSocket.sendMessageToAll("📢 Sync automático de stock activado cada " + minutes + " minutos.");
    }

    public synchronized void stop() {
        if (task != null && !task.isDone()) {
            task.cancel(false);
        }
        enabled = false;
        nextRun = null;
    }

    private void runSync() {
        try {
            WebSocket.sendMessageToAll("🔄 Sync automático de stock iniciado...");
            restLaudusWordP.ProductGetAllLaudusStock("008"); // bodega real con stock
            lastRun = Instant.now();
            nextRun = Instant.now().plusSeconds(intervalMinutes * 60L);
            WebSocket.sendMessageToAll("✅ Sync automático de stock completado.");
        } catch (Exception e) {
            WebSocket.sendMessageToAll("❌ Error en sync automático de stock: " + e.getMessage());
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getIntervalMinutes() { return intervalMinutes; }

    public String getLastRun() {
        return lastRun != null ? fmt(lastRun) : null;
    }

    public String getNextRun() {
        return nextRun != null && enabled ? fmt(nextRun) : null;
    }

    private String fmt(Instant i) {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.of("America/Santiago"))
                .format(i);
    }
}
