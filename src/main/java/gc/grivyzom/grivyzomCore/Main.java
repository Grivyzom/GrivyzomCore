package gc.grivyzom.grivyzomCore;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import org.slf4j.Logger;

@Plugin(id = "grivyzomcore", name = "GrivyzomCore", version = "0.1-SNAPSHOT", url = "www.grivyzom.com", authors = {"Francisco Fuentes"})
public class Main {

    @Inject
    private Logger logger;

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
    }
}
