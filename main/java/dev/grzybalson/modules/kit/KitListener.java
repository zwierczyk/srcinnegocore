package dev.grzybalson.modules.kit;

import dev.grzybalson.HollyCore;
import org.bukkit.event.Listener;

/**
 * Kit po śmierci wyłączony — zestaw {@code gracz} tylko przy pierwszym wejściu
 * ({@link KitManager#tryGiveFirstJoinKit}).
 */
public class KitListener implements Listener {

    public KitListener(HollyCore plugin) {
        // Zachowane pod rejestrację eventu / przyszłe hooki join.
    }
}
