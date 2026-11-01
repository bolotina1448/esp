package com.scaffold;

import net.minecraft.util.MovementInput;

/**
 * Обёртка над ванильным MovementInput игрока. Ванила вызывает updatePlayerMoveState()
 * внутри EntityPlayerSP.onLivingUpdate() — прямо перед расчётом движения и отправкой C03.
 * Это единственная точка без миксинов, где можно подменить поворот для сервера и сделать MoveFix.
 */
public class ScaffoldMovementInput extends MovementInput {

    /** Не final: другие моды-обёртки (например SafeWalk) могут перестраивать цепочку. */
    public MovementInput parent;
    private final ScaffoldHandler handler;

    public ScaffoldMovementInput(MovementInput parent, ScaffoldHandler handler) {
        this.parent = parent;
        this.handler = handler;
    }

    /** Есть ли в цепочке обёрток MovementInput обёртка нужного класса (обёртки хранят предка в поле "parent"). */
    public static boolean chainContains(MovementInput in, Class<?> type) {
        int guard = 0;
        while (in != null && guard++ < 16) {
            if (type.isInstance(in)) return true;
            try {
                Object parent = in.getClass().getField("parent").get(in);
                in = parent instanceof MovementInput ? (MovementInput) parent : null;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
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
