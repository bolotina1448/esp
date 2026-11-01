package com.safewalk;

import net.minecraft.util.MovementInput;

/**
 * Обёртка над MovementInput игрока. Ванила вызывает updatePlayerMoveState() внутри
 * EntityPlayerSP.onLivingUpdate() прямо перед расчётом движения — здесь мы и режем шаг у края.
 * Поле parent публичное и не final, чтобы цепочку обёрток могли перестраивать другие моды (Scaffold).
 */
public class SafeWalkInput extends MovementInput {

    public MovementInput parent;
    private final SafeWalkHandler handler;

    public SafeWalkInput(MovementInput parent, SafeWalkHandler handler) {
        this.parent = parent;
        this.handler = handler;
    }

    @Override
    public void updatePlayerMoveState() {
        if (parent != null) {
            parent.updatePlayerMoveState();
            this.moveStrafe = parent.moveStrafe;
            this.moveForward = parent.moveForward;
            this.jump = parent.jump;
            this.sneak = parent.sneak;
        }
        handler.onMoveInput(this);
    }
}
