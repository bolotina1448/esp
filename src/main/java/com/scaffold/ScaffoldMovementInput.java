package com.scaffold;

import net.minecraft.util.MovementInput;

/**
 * Обёртка над ванильным MovementInput игрока. Ванила вызывает updatePlayerMoveState()
 * внутри EntityPlayerSP.onLivingUpdate() — прямо перед расчётом движения и отправкой C03.
 * Это единственная точка без миксинов, где можно подменить поворот для сервера и сделать MoveFix.
 */
public class ScaffoldMovementInput extends MovementInput {

    public final MovementInput parent;
    private final ScaffoldHandler handler;

    public ScaffoldMovementInput(MovementInput parent, ScaffoldHandler handler) {
        this.parent = parent;
        this.handler = handler;
    }

    @Override
    public void updatePlayerMoveState() {
        parent.updatePlayerMoveState();
        this.moveStrafe = parent.moveStrafe;
        this.moveForward = parent.moveForward;
        this.jump = parent.jump;
        this.sneak = parent.sneak;
        handler.onMoveInput(this);
    }
}
