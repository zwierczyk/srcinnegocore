package dev.grzybalson.config.configs;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class AutoMessageConfig extends OkaeriConfig {

    private boolean enabled = true;
    private int interval = 30;
    private Map<Integer, List<String>> messages = new HashMap<>(Map.of(
        1, Arrays.asList(
            "&#FF0000&lZ&#FF001C&lE&#FF0037&lN&#FF0053&lT&#FF005D&lO&#FF0067&lR&#FF0059&lI&#FF004C&lA&#FF003E&l.&#FF001F&lP&#FF0000&lL &8\u00bb &fOdwied\u017a nasz sklep&8: &#FF0000w&#EC0505w&#D90A0Aw&#C60F0F.&#B21515z&#9F1A1Ae&#8C1F1Fn&#792424t&#8C1F1Fo&#9F1A1Ar&#B21515i&#C40F0Fa&#D70A0A.&#EA0505p&#FD0000l"
        ),
        2, Arrays.asList(
            "&#FF0000&lZ&#FF001C&lE&#FF0037&lN&#FF0053&lT&#FF005D&lO&#FF0067&lR&#FF0059&lI&#FF004C&lA&#FF003E&l.&#FF001F&lP&#FF0000&lL &8\u00bb &fNaszym oficjalnym i glownym komunikatorem jest &#0062FAd&#0074FBc&#0087FC.&#0099FCz&#00ABFDe&#009CFCn&#008EFCt&#007FFBo&#0071FBr&#0062FAi&#0062FAa&#0062FA.&#0062FAp&#0062FAl"
        ),
        3, Arrays.asList(
            "&#FF0000&lZ&#FF001C&lE&#FF0037&lN&#FF0053&lT&#FF005D&lO&#FF0067&lR&#FF0059&lI&#FF004C&lA&#FF003E&l.&#FF001F&lP&#FF0000&lL &8\u00bb &fChcesz odebrac zupelnie &adarmowa &fnagrode? &fwpisz \u00a7x\u00a72\u00a7a\u00a7c\u00a73\u00a7f\u00a7b/nagroda"
        )
    ));

    private boolean randomOrder = false;

    public boolean isEnabled() { return enabled; }
    public int getInterval() { return interval; }
    public Map<Integer, List<String>> getMessages() { return messages; }
    public boolean isRandomOrder() { return randomOrder; }
}
