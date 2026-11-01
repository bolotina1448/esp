package com.fastbreak.backtrack;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S40PacketDisconnect;

import java.util.ArrayDeque;
import java.util.concurrent.TimeUnit;

/**
 * Netty-хендлер, который стоит перед ванильным "packet_handler" и придерживает входящие пакеты.
 * Все методы, трогающие очередь, выполняются в event loop канала — гонок нет.
 * Порядок пакетов сохраняется всегда.
 */
public class PacketDelayer extends ChannelInboundHandlerAdapter {

    public static final String NAME = "fastbreak_backtrack";

    private static final class Held {
        final Object msg;
        final long time;

        Held(Object msg, long time) {
            this.msg = msg;
            this.time = time;
        }
    }

    private final ArrayDeque<Held> queue = new ArrayDeque<Held>();
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> task;

    // Пишутся из главного потока, читаются в event loop
    public volatile boolean active;
    public volatile boolean allPackets = true;
    public volatile int delayMs = 120;
    public volatile int queued;

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        task = ctx.executor().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                drain(false);
            }
        }, 5, 5, TimeUnit.MILLISECONDS);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        stop();
        drain(true);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        stop();
        queue.clear();
        queued = 0;
        super.channelInactive(ctx);
    }

    private void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // Телепорт/сетбек, респавн, вход, кик — сначала отдаём всё, что держали, потом этот пакет
        if (isFlushTrigger(msg)) {
            drain(true);
            ctx.fireChannelRead(msg);
            return;
        }

        boolean hold;
        if (allPackets) {
            hold = active || !queue.isEmpty();
        } else {
            hold = isEntityMove(msg) && (active || !queue.isEmpty());
        }

        if (hold) {
            queue.addLast(new Held(msg, System.currentTimeMillis()));
            queued = queue.size();
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    /** Выполняется только в event loop. */
    private void drain(boolean all) {
        if (ctx == null || queue.isEmpty()) return;
        long hold = (all || !active) ? 0 : delayMs;
        long now = System.currentTimeMillis();
        Held h;
        while ((h = queue.peekFirst()) != null && now - h.time >= hold) {
            queue.pollFirst();
            ctx.fireChannelRead(h.msg);
        }
        queued = queue.size();
    }

    /** Отдать всё прямо сейчас (можно вызывать из любого потока). */
    public void flushAsync() {
        final ChannelHandlerContext c = ctx;
        if (c == null) return;
        c.executor().execute(new Runnable() {
            @Override
            public void run() {
                drain(true);
            }
        });
    }

    private static boolean isFlushTrigger(Object msg) {
        return msg instanceof S08PacketPlayerPosLook
                || msg instanceof S07PacketRespawn
                || msg instanceof S01PacketJoinGame
                || msg instanceof S40PacketDisconnect;
    }

    private static boolean isEntityMove(Object msg) {
        return msg instanceof S14PacketEntity
                || msg instanceof S18PacketEntityTeleport
                || msg instanceof S19PacketEntityHeadLook;
    }
}
