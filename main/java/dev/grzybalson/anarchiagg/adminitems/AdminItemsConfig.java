package dev.grzybalson.anarchiagg.adminitems;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.NameModifier;
import eu.okaeri.configs.annotation.NameStrategy;
import eu.okaeri.configs.annotation.Names;
import java.util.List;

@Names(strategy=NameStrategy.HYPHEN_CASE, modifier=NameModifier.TO_LOWER_CASE)
public class AdminItemsConfig
extends OkaeriConfig {
    private GuiSettings gui = new GuiSettings();
    private List<String> blockedRegions = List.of("spawn");
    private String blockedRegionMessage = "&cNie mozesz uzywac eventowych przedmiotow na spawnie!";
    private LassoSettings lasso = new LassoSettings();
    private RodSettings rozga = new RodSettings();
    private PumpkinGrenadeSettings dyniowyGranat = new PumpkinGrenadeSettings();
    private MinerCurseSettings klatwaGornika = new MinerCurseSettings();
    private PokeBallSettings pokeBall = new PokeBallSettings();
    private SpikesSettings kolce = new SpikesSettings();
    private PandoraBoxSettings puszkaPandory = new PandoraBoxSettings();
    private IceWandSettings lodowaRozdzka = new IceWandSettings();
    private AntiCobwebSettings antyCobweb = new AntiCobwebSettings();
    private KangarooBagSettings torbaKangura = new KangarooBagSettings();
    private DestructionTridentSettings trojzabZniszczenia = new DestructionTridentSettings();
    private OlafNoseSettings nosOlafa = new OlafNoseSettings();
    private RejuvenationPotionSettings miksturaOdmlodzenia = new RejuvenationPotionSettings();
    private EnlightenmentMagnifierSettings lupaOswiecenia = new EnlightenmentMagnifierSettings();
    private GoatSettings koza = new GoatSettings();
    private StarChestplateSettings gwiezdnaKlata = new StarChestplateSettings();
    private HookSettings hak = new HookSettings();
    private TornadoSettings tornado = new TornadoSettings();
    private EscapeChorusSettings chorusUcieczki = new EscapeChorusSettings();
    private GoldenSkullSettings zlotaCzaszka = new GoldenSkullSettings();
    private DragonFireworkSettings smoczaFajerwerka = new DragonFireworkSettings();
    private InfiniteGoldenAppleSettings nieskonczoneZloteJapko = new InfiniteGoldenAppleSettings();
    private HellCardsSettings kartyPiekla = new HellCardsSettings();
    private MagicCrystalSettings magicznyKrysztal = new MagicCrystalSettings();
    private WuStaffSettings laskaMistrzaWu = new WuStaffSettings();
    private WinterHeartSettings serceZimy = new WinterHeartSettings();
    private DragonFangSettings smoczyKiel = new DragonFangSettings();

    public SpikesSettings getSpikes() {
        return this.kolce;
    }

    public void setSpikes(SpikesSettings kolce) {
        this.kolce = kolce;
    }

    public PandoraBoxSettings getPandoraBox() {
        return this.puszkaPandory;
    }

    public void setPandoraBox(PandoraBoxSettings puszkaPandory) {
        this.puszkaPandory = puszkaPandory;
    }

    public IceWandSettings getIceWand() {
        return this.lodowaRozdzka;
    }

    public void setIceWand(IceWandSettings lodowaRozdzka) {
        this.lodowaRozdzka = lodowaRozdzka;
    }

    public AntiCobwebSettings getAntiCobweb() {
        return this.antyCobweb;
    }

    public void setAntiCobweb(AntiCobwebSettings antyCobweb) {
        this.antyCobweb = antyCobweb;
    }

    public KangarooBagSettings getKangarooBag() {
        return this.torbaKangura;
    }

    public void setKangarooBag(KangarooBagSettings torbaKangura) {
        this.torbaKangura = torbaKangura;
    }

    public DestructionTridentSettings getDestructionTrident() {
        return this.trojzabZniszczenia;
    }

    public void setDestructionTrident(DestructionTridentSettings trojzabZniszczenia) {
        this.trojzabZniszczenia = trojzabZniszczenia;
    }

    public OlafNoseSettings getOlafNose() {
        return this.nosOlafa;
    }

    public void setOlafNose(OlafNoseSettings nosOlafa) {
        this.nosOlafa = nosOlafa;
    }

    public RejuvenationPotionSettings getRejuvenationPotion() {
        return this.miksturaOdmlodzenia;
    }

    public void setRejuvenationPotion(RejuvenationPotionSettings miksturaOdmlodzenia) {
        this.miksturaOdmlodzenia = miksturaOdmlodzenia;
    }

    public EnlightenmentMagnifierSettings getEnlightenmentMagnifier() {
        return this.lupaOswiecenia;
    }

    public void setEnlightenmentMagnifier(EnlightenmentMagnifierSettings lupaOswiecenia) {
        this.lupaOswiecenia = lupaOswiecenia;
    }

    public GoatSettings getGoat() {
        return this.koza;
    }

    public void setGoat(GoatSettings koza) {
        this.koza = koza;
    }

    public StarChestplateSettings getStarChestplate() {
        return this.gwiezdnaKlata;
    }

    public void setStarChestplate(StarChestplateSettings gwiezdnaKlata) {
        this.gwiezdnaKlata = gwiezdnaKlata;
    }

    public HookSettings getHook() {
        return this.hak;
    }

    public void setHook(HookSettings hak) {
        this.hak = hak;
    }

    public TornadoSettings getTornado() {
        return this.tornado;
    }

    public void setTornado(TornadoSettings tornado) {
        this.tornado = tornado;
    }

    public EscapeChorusSettings getEscapeChorus() {
        return this.chorusUcieczki;
    }

    public void setEscapeChorus(EscapeChorusSettings chorusUcieczki) {
        this.chorusUcieczki = chorusUcieczki;
    }

    public DragonFireworkSettings getDragonFirework() {
        return this.smoczaFajerwerka;
    }

    public void setDragonFirework(DragonFireworkSettings smoczaFajerwerka) {
        this.smoczaFajerwerka = smoczaFajerwerka;
    }

    public InfiniteGoldenAppleSettings getInfiniteGoldenApple() {
        return this.nieskonczoneZloteJapko;
    }

    public void setInfiniteGoldenApple(InfiniteGoldenAppleSettings nieskonczoneZloteJapko) {
        this.nieskonczoneZloteJapko = nieskonczoneZloteJapko;
    }

    public HellCardsSettings getHellCards() {
        return this.kartyPiekla;
    }

    public void setHellCards(HellCardsSettings kartyPiekla) {
        this.kartyPiekla = kartyPiekla;
    }

    public MagicCrystalSettings getMagicCrystal() {
        return this.magicznyKrysztal;
    }

    public void setMagicCrystal(MagicCrystalSettings magicznyKrysztal) {
        this.magicznyKrysztal = magicznyKrysztal;
    }

    public WuStaffSettings getWuStaff() {
        return this.laskaMistrzaWu;
    }

    public void setWuStaff(WuStaffSettings laskaMistrzaWu) {
        this.laskaMistrzaWu = laskaMistrzaWu;
    }

    public WinterHeartSettings getWinterHeart() {
        return this.serceZimy;
    }

    public void setWinterHeart(WinterHeartSettings serceZimy) {
        this.serceZimy = serceZimy;
    }

    public DragonFangSettings getDragonFang() {
        return this.smoczyKiel;
    }

    public void setDragonFang(DragonFangSettings smoczyKiel) {
        this.smoczyKiel = smoczyKiel;
    }

    public GoldenSkullSettings getGoldenSkull() {
        return this.zlotaCzaszka;
    }

    public GuiSettings getGui() {
        return this.gui;
    }

    public void setGui(GuiSettings gui) {
        this.gui = gui;
    }

    public List<String> getBlockedRegions() {
        return this.blockedRegions;
    }

    public void setBlockedRegions(List<String> blockedRegions) {
        this.blockedRegions = blockedRegions;
    }

    public String getBlockedRegionMessage() {
        return this.blockedRegionMessage;
    }

    public void setBlockedRegionMessage(String blockedRegionMessage) {
        this.blockedRegionMessage = blockedRegionMessage;
    }

    public LassoSettings getLasso() {
        return this.lasso;
    }

    public void setLasso(LassoSettings lasso) {
        this.lasso = lasso;
    }

    public RodSettings getRod() {
        return this.rozga;
    }

    public void setRod(RodSettings rozga) {
        this.rozga = rozga;
    }

    public MinerCurseSettings getMinerCurse() {
        return this.klatwaGornika;
    }

    public void setMinerCurse(MinerCurseSettings klatwaGornika) {
        this.klatwaGornika = klatwaGornika;
    }

    public PokeBallSettings getPokeBall() {
        return this.pokeBall;
    }

    public void setPokeBall(PokeBallSettings pokeBall) {
        this.pokeBall = pokeBall;
    }

    public PumpkinGrenadeSettings getPumpkinGrenade() {
        return this.dyniowyGranat;
    }

    public void setPumpkinGrenade(PumpkinGrenadeSettings dyniowyGranat) {
        this.dyniowyGranat = dyniowyGranat;
    }

    public static class GuiSettings
    extends OkaeriConfig {
        private String itemsGuiTitle = "&8AdminItems";

        public String getItemsGuiTitle() {
            return this.itemsGuiTitle;
        }

        public void setItemsGuiTitle(String itemsGuiTitle) {
            this.itemsGuiTitle = itemsGuiTitle;
        }
    }

    public static class LassoSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&6&l\u029f\u1d00\ua731\ua731\u1d0f";
        private int customModelData = 0;
        private String texture = "";
        private List<String> lore = List.of("&8x &fPo zarzuceniu nim, na wroga", "&8x &fnie mo\u017ce si\u0119 on", "&8x &fod nas oddali\u0107 &epowy\u017cej 10 kratek", "&8x &fPo ponownym klikni\u0119ciu &aprzyci\u0105ga go &fdo nas", "&0 ", "&8x &6Kliknij prawym na gracza, aby aktywowa\u0107!");
        private int maxDistance = 10;
        private int cooldown = 15;
        private int targetCooldown = 120;
        private int duration = 5;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public String getTexture() {
            return this.texture;
        }

        public void setTexture(String texture) {
            this.texture = texture;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getMaxDistance() {
            return this.maxDistance;
        }

        public void setMaxDistance(int maxDistance) {
            this.maxDistance = maxDistance;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getTargetCooldown() {
            return this.targetCooldown;
        }

        public void setTargetCooldown(int targetCooldown) {
            this.targetCooldown = targetCooldown;
        }

        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }

    public static class RodSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#6D5D3F&l\u0280&#837252&l\u00f3&#998765&l\u1d22&#AE9B77&l\u0262&#C4B08A&l\u1d00";
        private int customModelData = 1001;
        private int cooldown = 60;
        private int targetCooldown = 120;
        private String texture = "";
        private List<String> lore = List.of("&8x &fPo uderzeniu przeciwnika &cblokujemy &fmu", "&8x &fmo\u017cliwo\u015b\u0107 korzystania z &7elytry", "&0 ", "&8x &fPrzedmiot pochodzi z &6skrzyni miko\u0142aja 2024");
        private int elytraBlockDuration = 10;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getTargetCooldown() {
            return this.targetCooldown;
        }

        public void setTargetCooldown(int targetCooldown) {
            this.targetCooldown = targetCooldown;
        }

        public String getTexture() {
            return this.texture;
        }

        public void setTexture(String texture) {
            this.texture = texture;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getElytraBlockDuration() {
            return this.elytraBlockDuration;
        }

        public void setElytraBlockDuration(int elytraBlockDuration) {
            this.elytraBlockDuration = elytraBlockDuration;
        }
    }

    public static class PumpkinGrenadeSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FFAB57&l\u1d05&#FCA752&l\u028f&#F9A24C&l\u0274&#F59E47&l\u026a&#F29A41&l\u1d0f&#EF953C&l\u0262&#EC9136&l\u029f&#E88D31&l\u1d0f&#E5892C&l\u1d21&#E28426&l\u028f &#DB7C1B&l\u0262&#D87716&l\u0280&#D57310&l\u1d00&#D26F0B&l\u0274&#CE6A05&l\u1d00&#CB6600&l\u1d1b";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8x &fJednorazowy przedmiot rzucany, po u\u017cyciu he\u0142my", "&8x &fos\u00f3b dotkni\u0119tych czarem &6zostaj\u0105 zamienione w dynie", "&8x &fna okres &akilku sekund&!", "&0 ", "&8x &7Przedmiot pochodzi z eventu &6HALLOWEEN 2024");
        private int duration = 5;
        private int radius = 5;
        private int cooldown = 30;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public int getRadius() {
            return this.radius;
        }

        public void setRadius(int radius) {
            this.radius = radius;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class MinerCurseSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#00BC08&l\u1d0b&#0EBD16&l\u029f&#1DBF24&l\u1d00&#2BC031&l\u1d1b&#39C23F&l\u1d21&#48C34D&l\u1d00 &#64C668&l\u0262&#72C876&l\u00f3&#81C984&l\u0280&#8FCB92&l\u0274&#9DCC9F&l\u026a&#ACCEAD&l\u1d0b&#BACFBB&l\u1d00";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo uderzeniu nim gracza, mamy szans\u0119 na zebranie", "&8\u2715 &fmu mo\u017cliwo\u015bci &6rozkopywania blok\u00f3w &fprzez kilka sekund", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2024");
        private int duration = 10;
        private int cooldown = 60;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class PokeBallSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&c&l\u1d18\u1d0f\u1d0b\u1d07&f&l\u0299\u1d00\u029f\u029f";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo kilkni\u0119ciu &6prawym &fwyrzucamy &c\u1d18\u1d0f\u1d0b\u1d07\u0299\u1d00\u029f\u029f&f, je\u015bli trafimy", "&8\u2715 &fnim w gracza mamy szanse na &aprzeteleportowanie go &fdo nas", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2024");
        private double chance = 50.0;
        private int cooldown = 30;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public double getChance() {
            return this.chance;
        }

        public void setChance(double chance) {
            this.chance = chance;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class SpikesSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#D89D2B&l\u1d0b&#DCA63C&l\u1d0f&#E1AE4D&l\u029f&#E5B75E&l\u1d04&#E9C06F&l\u1d07 &#F2D190&l\u1d0a&#F6DAA1&l\u1d07&#FBE2B2&l\u017c&#FFEBC3&l\u1d00";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu przez kilka sekund blokujemy", "&8\u2715 &fuderzenia zadawane przez rywali", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2024");
        private int duration = 5;
        private int cooldown = 60;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class PandoraBoxSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#7F00BC&l\u1d18&#870FC1&l\u1d1c&#8E1FC6&ls&#962ECB&l\u1d22&#9D3ED1&l\u1d0b&#A54DD6&l\u1d00 &#B46CE0&l\u1d18&#BC7CE5&l\u1d00&#C48BEA&l\u0274&#CB9BF0&l\u1d05&#D3AAF5&l\u1d0f&#DABAFA&l\u0280&#E2C9FF&l\u028f";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu, mamy szans\u0119 na otrzymanie pozytywnego", "&8\u2715 &fefektu &cSi\u0142y II &flub negatywnego &3Spowolnienia", "", "&8\u2715 &4&lUwaga! &cPrzedmiot jest jednorazowy", "", "&8\u2715 &7Przedmiot pochodzi z eventu &e\u1d18\u1d0fs\u1d22\u1d1c\u1d0b\u026a\u1d21\u1d00\u1d04\u1d22\u1d07 s\u1d0b\u1d00\u0280\u0299\u00f3\u1d21 2024");
        private int cooldown = 30;
        private double positiveChance = 50.0;
        private int effectDuration = 5;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public double getPositiveChance() {
            return this.positiveChance;
        }

        public void setPositiveChance(double positiveChance) {
            this.positiveChance = positiveChance;
        }

        public int getEffectDuration() {
            return this.effectDuration;
        }

        public void setEffectDuration(int effectDuration) {
            this.effectDuration = effectDuration;
        }
    }

    public static class IceWandSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#5790FF&l\u029f&#6298F9&l\u1d0f&#6CA1F2&l\u1d05&#77A9EC&l\u1d0f&#82B2E6&l\u1d21&#8CBADF&l\u1d00 &#A2CBD3&l\u0280&#ADD3CD&l\u1d0f&#B7DBC6&l\u1d22&#C2E4C0&l\u1d05&#CDECBA&l\u1d22&#D7F5B3&l\u1d0b&#E2FDAD&l\u1d00";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu zabiera przeciwnikom wok\u00f3\u0142", "&8\u2715 &fwszystkie &dpozytywne efekty potek", "", "&8\u2715 &fPrzedmiot mo\u017cna trafi\u0107 w &b&lLodowej &fSkrzyni", "", "&eKilknij prawym, aby u\u017cy\u0107");
        private int cooldown = 45;
        private int radius = 5;
        private int oneHitTotemSeconds = 5;
        private double totemMaxHealth = 4.0;
        private double totemMaxAbsorption = 8.0;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getRadius() {
            return this.radius;
        }

        public void setRadius(int radius) {
            this.radius = radius;
        }

        public int getOneHitTotemSeconds() {
            return this.oneHitTotemSeconds;
        }

        public void setOneHitTotemSeconds(int oneHitTotemSeconds) {
            this.oneHitTotemSeconds = oneHitTotemSeconds;
        }

        public double getTotemMaxHealth() {
            return this.totemMaxHealth;
        }

        public void setTotemMaxHealth(double totemMaxHealth) {
            this.totemMaxHealth = totemMaxHealth;
        }

        public double getTotemMaxAbsorption() {
            return this.totemMaxAbsorption;
        }

        public void setTotemMaxAbsorption(double totemMaxAbsorption) {
            this.totemMaxAbsorption = totemMaxAbsorption;
        }
    }

    public static class AntiCobwebSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&6&lUsuwacz paj\u0119czyn";
        private int customModelData = 1001;
        private int cooldown = 30;
        private String texture = "";
        private List<String> lore = List.of("&8x &fU\u017cyj, aby usun\u0105\u0107 paj\u0119czyny w promieniu 5 blok\u00f3w", "&0 ", "&8x &eKliknij prawym, aby u\u017cy\u0107!");

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public String getTexture() {
            return this.texture;
        }

        public void setTexture(String texture) {
            this.texture = texture;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }
    }

    public static class KangarooBagSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FFBF65&lᴡ&#FFC15D&lᴏ&#FFC354&lʀ&#FFC64C&lᴇ&#FFC843&lᴋ &#FFCC33&lɴ&#FFCE2A&lɪ&#FFD022&lɴ&#FFD319&lᴊ&#FFD511&lᴀ";
        private int customModelData = 1001;
        private List<String> lore = List.of(
            "&8× &7Trzymając &fworek ninja &7w ekwipunku trafiają do niego",
            "&8× &7wszystkie &ecenne &7przedmioty z &czabitych graczy",
            "",
            "&8× &6Kliknij prawym, aby otworzyć!"
        );
        private int cooldown = 5;
        private String messageItemsStored = "&#FFBF65&lWorek ninja &8:: &aPrzedmioty ofiary trafiły bezpośrednio do twojego worka!";
        private String messageFull = "&#FFBF65&lWorek ninja &8:: &cTwój worek jest pełny, reszta przedmiotów wypadła na ziemię!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public String getMessageItemsStored() {
            return this.messageItemsStored;
        }

        public void setMessageItemsStored(String messageItemsStored) {
            this.messageItemsStored = messageItemsStored;
        }

        public String getMessageFull() {
            return this.messageFull;
        }

        public void setMessageFull(String messageFull) {
            this.messageFull = messageFull;
        }
    }

    public static class DestructionTridentSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#3AB4FF&l\u1d1b&#3DB5FF&l\u0280&#41B6FF&l\u00f3&#44B7FF&l\u1d0a&#48B7FF&l\u1d22&#4BB8FF&l\u1d00&#4FB9FF&l\u0299 &#56BBFF&l\u1d22&#59BCFF&l\u0274&#5CBCFF&l\u026a&#60BDFF&ls&#63BEFF&l\u1d22&#67BFFF&l\u1d04&#6AC0FF&l\u1d22&#6EC1FF&l\u1d07&#71C1FF&l\u0274&#75C2FF&l\u026a&#78C3FF&l\u1d00";
        private int customModelData = 0;
        private List<String> lore = List.of("&8\u2715 &fGdy uderzamy gracza, nadajemy", "&8\u2715 &fmu pot\u0119\u017cn\u0105 serie &bpiorun\u00f3w &foraz mamy szanse na", "&8\u2715 &ccz\u0119\u015bciowe zniszczenie &fjego zbroi");
        private int cooldown = 30;
        private int lightningStrikes = 3;
        private int minArmorDamage = 2;
        private int maxArmorDamage = 5;
        private String messageUse = "&#3AB4FF&l\u1d1b&#3DB5FF&l\u0280&#41B6FF&l\u00f3&#44B7FF&l\u1d0a&#48B7FF&l\u1d22&#4BB8FF&l\u1d00&#4FB9FF&l\u0299 &#56BBFF&l\u1d22&#59BCFF&l\u0274&#5CBCFF&l\u026a&#60BDFF&ls&#63BEFF&l\u1d22&#67BFFF&l\u1d04&#6AC0FF&l\u1d22&#6EC1FF&l\u1d07&#71C1FF&l\u0274&#75C2FF&l\u026a&#78C3FF&l\u1d00 &8:: &aPora\u017cono przeciwnika moc\u0105 destrukcji!";
        private String messageSpawn = "&#3AB4FF&l\u1d1b&#3DB5FF&l\u0280&#41B6FF&l\u00f3&#44B7FF&l\u1d0a&#48B7FF&l\u1d22&#4BB8FF&l\u1d00&#4FB9FF&l\u0299 &#56BBFF&l\u1d22&#59BCFF&l\u0274&#5CBCFF&l\u026a&#60BDFF&ls&#63BEFF&l\u1d22&#67BFFF&l\u1d04&#6AC0FF&l\u1d22&#6EC1FF&l\u1d07&#71C1FF&l\u0274&#75C2FF&l\u026a&#78C3FF&l\u1d00 &8:: &cNie mo\u017cesz u\u017cywa\u0107 tego przedmiotu na spawnie!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getLightningStrikes() {
            return this.lightningStrikes;
        }

        public void setLightningStrikes(int lightningStrikes) {
            this.lightningStrikes = lightningStrikes;
        }

        public int getMinArmorDamage() {
            return this.minArmorDamage;
        }

        public void setMinArmorDamage(int minArmorDamage) {
            this.minArmorDamage = minArmorDamage;
        }

        public int getMaxArmorDamage() {
            return this.maxArmorDamage;
        }

        public void setMaxArmorDamage(int maxArmorDamage) {
            this.maxArmorDamage = maxArmorDamage;
        }

        public String getMessageUse() {
            return this.messageUse;
        }

        public void setMessageUse(String messageUse) {
            this.messageUse = messageUse;
        }

        public String getMessageSpawn() {
            return this.messageSpawn;
        }

        public void setMessageSpawn(String messageSpawn) {
            this.messageSpawn = messageSpawn;
        }
    }

    public static class OlafNoseSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FFFFFF&l\u0274&#FFF1DF&l\u1d0f&#FFE4BF&ls &#FFC880&l\u1d0f&#FFBA60&l\u029f&#FFAD40&l\u1d00&#FF9F20&l\u0493&#FF9100&l\u1d00";
        private int customModelData = 1004;
        private List<String> lore = List.of("&8\u2715 &fPo uderzeniu gracza odpycha go", "&8\u2715 &fprzy pomocy zakl\u0119cia &cOdrzut IV", "", "&8\u2715 &fPrzedmiot mo\u017cna trafi\u0107 w &b&lLodowej &fSkrzyni");
        private int cooldown = 10;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class RejuvenationPotionSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF51EA&l\u1d0d&#F755EB&l\u026a&#F059EC&l\u1d0b&#E85DED&ls&#E061EE&l\u1d1b&#D964F0&l\u1d1c&#D168F1&l\u0280&#C96CF2&l\u1d00 &#BA74F4&l\u1d0f&#B278F5&l\u1d05&#AA7CF6&l\u1d0d&#A380F7&l\u029f&#9B84F8&l\u1d0f&#9388F9&l\u1d05&#8C8BFB&l\u1d22&#848FFC&l\u1d07&#7C93FD&l\u0274&#7597FE&l\u026a&#6D9BFF&l\u1d00";
        private int customModelData = 1005;
        private List<String> lore = List.of("&8\u2715 &fPo wypiciu, natychmiastowo regeneruje", "&8\u2715 &fnasze &azdrowie &fdo pe\u0142na", "", "&8\u2715 &fPrzedmiot mo\u017cna trafi\u0107 w &b&lLodowej &fSkrzyni");
        private int cooldown = 60;
        private int goldHeartsDuration = 120;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getGoldHeartsDuration() {
            return this.goldHeartsDuration;
        }

        public void setGoldHeartsDuration(int goldHeartsDuration) {
            this.goldHeartsDuration = goldHeartsDuration;
        }
    }

    public static class EnlightenmentMagnifierSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00";
        private int customModelData = 1009;
        private List<String> lore = List.of("&8\u2715 &fPo uderzeniu nim gracza, dostajemy informacj\u0119", "&8\u2715 &fna czacie o &bstanie jego zbroi &foraz ilo\u015bci &6totem\u00f3w &fw ekwipunku", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2024");
        private int cooldown = 3;
        private String headerMessage = "&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00 &8:: &aStan zbroi gracza &2{PLAYER}:";
        private String armorLineMessage = "&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00 &8:: &b{ITEM} &8- &b{DURABILITY}&8/&3{MAX_DURABILITY}";
        private String totemMessage = "&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00 &8:: &aIlo\u015b\u0107 totem\u00f3w: &6{COUNT}";
        private String messageSpawn = "&#F6FFC5&l\u029f&#E4FFC9&l\u1d1c&#D3FFCD&l\u1d18&#C1FFD1&l\u1d00 &#9EFFDA&l\u1d0f&#8DFFDE&l\u015b&#7BFFE2&l\u1d21&#69FFE6&l\u026a&#58FFEA&l\u1d07&#46FFEE&l\u1d04&#35FFF3&l\u1d07&#23FFF7&l\u0274&#12FFFB&l\u026a&#00FFFF&l\u1d00 &8:: &cNie mo\u017cesz sprawdza\u0107 graczy na spawnie!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public String getHeaderMessage() {
            return this.headerMessage;
        }

        public void setHeaderMessage(String headerMessage) {
            this.headerMessage = headerMessage;
        }

        public String getArmorLineMessage() {
            return this.armorLineMessage;
        }

        public void setArmorLineMessage(String armorLineMessage) {
            this.armorLineMessage = armorLineMessage;
        }

        public String getTotemMessage() {
            return this.totemMessage;
        }

        public void setTotemMessage(String totemMessage) {
            this.totemMessage = totemMessage;
        }

        public String getMessageSpawn() {
            return this.messageSpawn;
        }

        public void setMessageSpawn(String messageSpawn) {
            this.messageSpawn = messageSpawn;
        }
    }

    public static class GoatSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF2D00&l\u1d0b&#F63700&l\u1d0f&#EE4000&l\u1d22&#E54A00&l\u1d00";
        private int customModelData = 1006;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu na przeciwniku, mamy szans\u0119 na", "&8\u2715 &fzamkni\u0119cie nas w &dklatce&f, w kt\u00f3rej", "&8\u2715 &fmamy &61 vs 1 &fprzez okre\u015blony czas", "", "&8\u2715 &7Przedmiot pochodzi z eventu &es\u1d22\u1d0b\u1d0f\u029f\u0274\u1d07\u0262\u1d0f 2024");
        private int cooldown = 60;
        private int targetCooldown = 120;
        private double borderSize = 15.0;
        private int duration = 10;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public int getTargetCooldown() {
            return this.targetCooldown;
        }

        public void setTargetCooldown(int targetCooldown) {
            this.targetCooldown = targetCooldown;
        }

        public double getBorderSize() {
            return this.borderSize;
        }

        public void setBorderSize(double borderSize) {
            this.borderSize = borderSize;
        }

        public int getDuration() {
            return this.duration;
        }

        public void setDuration(int duration) {
            this.duration = duration;
        }
    }

    public static class StarChestplateSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF0000&l\u0262&#FF2A00&l\u1d21&#FF5500&l\u026a&#FF7F00&l\u1d07&#FFAA00&l\u1d22&#FFD400&l\u1d05&#FFFF00&l\u0274&#AAFF00&l\u028f &#00FF00&l\u0274&#00AA55&l\u1d00&#0055AA&l\u1d18&#0000FF&l\u026a&#1900D5&l\u1d07&#3200AC&l\u0280&#4B0082&l\u015b&#63009D&l\u0274&#7C00B8&l\u026a&#9400D3&l\u1d0b";
        private int customModelData = 1013;
        private List<String> lore = List.of("&#3F58FF&l[&#3A61FC&l\u2764&#356BF9&l] &#2A7DF2&lO&#2586EF&lc&#2090EC&lh&#1A99E9&lr&#15A2E6&lo&#10ABE3&ln&#0BB5DF&la &#00C7D9&l44", "", "&8\u2715 &6Przedmiot jedyny w swoim rodzaju!", "&8\u2715 &7Posiada go &etylko jedna osoba &7na ca\u0142ym serwerze", "&8\u2715 &7oraz nie da si\u0119 go &c&lzniszczy\u0107 &7w \u017caden spos\u00f3b!", "", "&8\u2630 &eBonusy &7po za\u0142o\u017ceniu:", "&8\u27a5 &cSi\u0142a II", "&8\u27a5 &bSzybko\u015b\u0107 I", "&8\u27a5 &dSzanse na zablokowanie uderzenia");
        private double blockChance = 22.5;
        private String messageBlocked = "&#FF0000&l\u0262&#FF2A00&l\u1d21&#FF5500&l\u026a&#FF7F00&l\u1d07&#FFAA00&l\u1d22&#FFD400&l\u1d05&#FFFF00&l\u0274&#AAFF00&l\u028f &#00FF00&l\u0274&#00AA55&l\u1d00&#0055AA&l\u1d18&#0000FF&l\u026a&#1900D5&l\u1d07&#3200AC&l\u0280&#4B0082&l\u015b&#63009D&l\u0274&#7C00B8&l\u026a&#9400D3&l\u1d0b &8:: &cZablokowano uderzenie!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public double getBlockChance() {
            return this.blockChance;
        }

        public void setBlockChance(double blockChance) {
            this.blockChance = blockChance;
        }

        public String getMessageBlocked() {
            return this.messageBlocked;
        }

        public void setMessageBlocked(String messageBlocked) {
            this.messageBlocked = messageBlocked;
        }
    }

    public static class HookSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF5757&l\u029c&#FF3A3A&l\u1d00&#FF1C1C&l\u1d0b";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &7Przyci\u0105ga ci\u0119 w miejsce &crzucenia &7haka", "", "&8\u2715 &7Przedmiot pochodzi z eventu &b\u1d00\u0493\u1d0b 2024");
        private int cooldown = 5;
        private double pullStrength = 1.5;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public double getPullStrength() {
            return this.pullStrength;
        }

        public void setPullStrength(double pullStrength) {
            this.pullStrength = pullStrength;
        }
    }

    public static class TornadoSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#BC4FFF&l\u1d1b&#C059FF&l\u1d0f&#C362FF&l\u0280&#C76CFF&l\u0274&#CB75FF&l\u1d00&#CE7FFF&l\u1d05&#D288FF&l\u1d0f";
        private int customModelData = 1008;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu przyci\u0105ga do nas", "&8\u2715 &ewszystkich graczy &fw odr\u0119bie kilku kratek", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2025");
        private int cooldown = 45;
        private double radius = 10.0;
        private double pullStrength = 1.5;
        private String messageUse = "&#BC4FFF&l\u1d1b&#C059FF&l\u1d0f&#C362FF&l\u0280&#C76CFF&l\u0274&#CB75FF&l\u1d00&#CE7FFF&l\u1d05&#D288FF&l\u1d0f &8:: &aU\u017cy\u0142e\u015b pot\u0119\u017cnego tornada!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public double getRadius() {
            return this.radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public double getPullStrength() {
            return this.pullStrength;
        }

        public void setPullStrength(double pullStrength) {
            this.pullStrength = pullStrength;
        }

        public String getMessageUse() {
            return this.messageUse;
        }

        public void setMessageUse(String messageUse) {
            this.messageUse = messageUse;
        }
    }

    public static class EscapeChorusSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF8AF6&l\u1d04&#FB8DF2&l\u029c&#F78FEF&l\u1d0f&#F292EB&l\u0280&#EE94E7&l\u1d1c&#EA97E4&ls &#E29CDD&l\u1d1c&#DD9FD9&l\u1d04&#D9A1D5&l\u026a&#D5A4D2&l\u1d07&#D1A6CE&l\u1d04&#CCA9CA&l\u1d22&#C8ABC7&l\u1d0b&#C4AEC3&l\u026a";
        private int customModelData = 1010;
        private List<String> lore = List.of("&8\u2715 &fPo u\u017cyciu mamy szanse na &dprzeteleportowanie", "&8\u2715 &fsi\u0119 do &elosowego gracza &fw obr\u0119bie kilku kratek", "", "&8\u2715 &7Przedmiot pochodzi z eventu &c\u1d05\u1d22\u026a\u1d07\u0144 \u1d05\u1d22\u026a\u1d07\u1d04\u1d0b\u1d00 2024");
        private int cooldown = 25;
        private double radius = 15.0;
        private String messageSuccess = "&#FF8AF6&l\u1d04&#FB8DF2&l\u029c&#F78FEF&l\u1d0f&#F292EB&l\u0280&#EE94E7&l\u1d1c&#EA97E4&ls &#E29CDD&l\u1d1c&#DD9FD9&l\u1d04&#D9A1D5&l\u026a&#D5A4D2&l\u1d07&#D1A6CE&l\u1d04&#CCA9CA&l\u1d22&#C8ABC7&l\u1d0b&#C4AEC3&l\u026a &8:: &aPrzeteleportowano do losowego gracza.";
        private String messageFail = "&#FF8AF6&l\u1d04&#FB8DF2&l\u029c&#F78FEF&l\u1d0f&#F292EB&l\u0280&#EE94E7&l\u1d1c&#EA97E4&ls &#E29CDD&l\u1d1c&#DD9FD9&l\u1d04&#D9A1D5&l\u026a&#D5A4D2&l\u1d07&#D1A6CE&l\u1d04&#CCA9CA&l\u1d22&#C8ABC7&l\u1d0b&#C4AEC3&l\u026a &8:: &cW pobli\u017cu nie ma \u017cadnego gracza, do kt\u00f3rego m\u00f3g\u0142by\u015b uciec!";
        private String messageSpawn = "&#FF8AF6&l\u1d04&#FB8DF2&l\u029c&#F78FEF&l\u1d0f&#F292EB&l\u0280&#EE94E7&l\u1d1c&#EA97E4&ls &#E29CDD&l\u1d1c&#DD9FD9&l\u1d04&#D9A1D5&l\u026a&#D5A4D2&l\u1d07&#D1A6CE&l\u1d04&#CCA9CA&l\u1d22&#C8ABC7&l\u1d0b&#C4AEC3&l\u026a &8:: &cNie mo\u017cesz u\u017cywa\u0107 tego przedmiotu na spawnie!";
        private String messageKozaBlocked = "&#FF8AF6&l\u1d04&#FB8DF2&l\u029c&#F78FEF&l\u1d0f&#F292EB&l\u0280&#EE94E7&l\u1d1c&#EA97E4&ls &#E29CDD&l\u1d1c&#DD9FD9&l\u1d04&#D9A1D5&l\u026a&#D5A4D2&l\u1d07&#D1A6CE&l\u1d04&#CCA9CA&l\u1d22&#C8ABC7&l\u1d0b&#C4AEC3&l\u026a &8:: &cNie mo\u017cesz u\u017cy\u0107 chorusa podczas kozy!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public double getRadius() {
            return this.radius;
        }

        public void setRadius(double radius) {
            this.radius = radius;
        }

        public String getMessageSuccess() {
            return this.messageSuccess;
        }

        public void setMessageSuccess(String messageSuccess) {
            this.messageSuccess = messageSuccess;
        }

        public String getMessageFail() {
            return this.messageFail;
        }

        public void setMessageFail(String messageFail) {
            this.messageFail = messageFail;
        }

        public String getMessageSpawn() {
            return this.messageSpawn;
        }

        public void setMessageSpawn(String messageSpawn) {
            this.messageSpawn = messageSpawn;
        }

        public String getMessageKozaBlocked() {
            return this.messageKozaBlocked;
        }

        public void setMessageKozaBlocked(String messageKozaBlocked) {
            this.messageKozaBlocked = messageKozaBlocked;
        }
    }

    public static class GoldenSkullSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#989000&l\u1d22&#A19800&l\u029f&#A9A000&l\u1d0f&#B2A900&l\u1d1b&#BAB100&l\u1d00 &#CCC100&l\u1d04&#D4C900&l\u1d22&#DDD100&l\u1d00&#E5DA00&ls&#EEE200&l\u1d22&#F6EA00&l\u1d0b&#FFF200&l\u1d00";
        private int customModelData = 10003;
        private String texture = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWI1MjIxYjI3NjRjZWY2Nzg2YWMxNWVhMDc1NzY4N2UyM2I5NTM2ODY0NTIwMzI0ZGQ0MzkzYjdhYmVhZDFkMSJ9fX0=";
        private List<String> lore = List.of("&8\u2715 &fPosiadaj\u0105c j\u0105 w ekwipunku, po \u015bmierci", "&8\u2715 &anie stracimy &f\u017cadnych przedmiot\u00f3w", "", "&8\u2715 &4&lUwaga! &cPrzedmiot jest jednorazowy", "", "&8\u2715 &7Przedmiot pochodzi z eventu &e\u1d18\u1d0fs\u1d22\u1d1c\u1d0b\u026a\u1d21\u1d00\u1d04\u1d22\u1d07 s\u1d0b\u1d00\u0280\u0299\u00f3\u1d21 2024");
        private String messageUsed = "&#989000&l\u1d22&#A19800&l\u029f&#A9A000&l\u1d0f&#B2A900&l\u1d1b&#BAB100&l\u1d00 &#CCC100&l\u1d04&#D4C900&l\u1d22&#DDD100&l\u1d00&#E5DA00&ls&#EEE200&l\u1d22&#F6EA00&l\u1d0b&#FFF200&l\u1d00 &8:: &aTw\u00f3j ekwipunek zosta\u0142 uratowany!";
        private String messageKillerInfo = "&#989000&l\u1d22&#A19800&l\u029f&#A9A000&l\u1d0f&#B2A900&l\u1d1b&#BAB100&l\u1d00 &#CCC100&l\u1d04&#D4C900&l\u1d22&#DDD100&l\u1d00&#E5DA00&ls&#EEE200&l\u1d22&#F6EA00&l\u1d0b&#FFF200&l\u1d00 &8:: &cZabi\u0142e\u015b gracza &4{PLAYER}, &clecz posiada\u0142 on z\u0142ot\u0105 czaszke!";

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public String getTexture() {
            return this.texture;
        }

        public void setTexture(String texture) {
            this.texture = texture;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public String getMessageUsed() {
            return this.messageUsed;
        }

        public void setMessageUsed(String messageUsed) {
            this.messageUsed = messageUsed;
        }

        public String getMessageKillerInfo() {
            return this.messageKillerInfo;
        }

        public void setMessageKillerInfo(String messageKillerInfo) {
            this.messageKillerInfo = messageKillerInfo;
        }
    }

    public static class DragonFireworkSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#8F32FF&lSmocza Fajerwerka";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &7Nieskonczona fajerwerka", "", "&8\u2715 &7Przedmiot pochodzi z eventu &b\u1d00\u0493\u1d0b 2024");
        private double cooldown = 0.3;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public double getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(double cooldown) {
            this.cooldown = cooldown;
        }
    }

    public static class InfiniteGoldenAppleSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&#FF4BE2&ls&#FF54E3&l\u029f&#FF5DE5&l\u1d0f&#FF65E6&l\u1d05&#FF6EE8&l\u1d0b&#FF77E9&l\u026a&#FF80EA&l\u1d07 &#FF91ED&l\u1d0a&#FF9AEE&l\u1d00&#FFA3F0&l\u0299&#FFABF1&l\u029f&#FFB4F3&l\u1d0b&#FFBDF4&l\u1d0f";
        private int customModelData = 1001;
        private List<String> lore = List.of("&8\u2715 &fPo zjedzeniu mamy szanse na", "&8\u2715 &fotrzymanie wi\u0119kszej ilo\u015bci &edodatkowych serc", "", "&8\u2715 &7Przedmiot pochodzi z eventu &6\u029c\u1d00\u029f\u029f\u1d0f\u1d21\u1d07\u1d07\u0274 2024");
        private int cooldown = 0;
        private double extraGoldenHeartsChance = 20.0;
        private int foodLevel = 4;
        private float saturation = 9.6f;

        public boolean isEnabled() {
            return this.enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getCustomModelData() {
            return this.customModelData;
        }

        public void setCustomModelData(int customModelData) {
            this.customModelData = customModelData;
        }

        public List<String> getLore() {
            return this.lore;
        }

        public void setLore(List<String> lore) {
            this.lore = lore;
        }

        public int getCooldown() {
            return this.cooldown;
        }

        public void setCooldown(int cooldown) {
            this.cooldown = cooldown;
        }

        public double getExtraGoldenHeartsChance() {
            return this.extraGoldenHeartsChance;
        }

        public void setExtraGoldenHeartsChance(double extraGoldenHeartsChance) {
            this.extraGoldenHeartsChance = extraGoldenHeartsChance;
        }

        public int getFoodLevel() {
            return this.foodLevel;
        }

        public void setFoodLevel(int foodLevel) {
            this.foodLevel = foodLevel;
        }

        public float getSaturation() {
            return this.saturation;
        }

        public void setSaturation(float saturation) {
            this.saturation = saturation;
        }
    }

    public static class HellCardsSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&x&F&F&2&D&2&D&lᴋ&x&F&F&3&A&2&A&lᴀ&x&F&F&4&7&2&7&lʀ&x&F&F&5&4&2&4&lᴛ&x&F&F&6&1&2&1&lʏ&x&F&F&6&E&1&E&l &x&F&F&7&B&1&B&lᴘ&x&F&F&8&8&1&8&lɪ&x&F&F&9&5&1&5&lᴇ&x&F&F&A&2&1&2&lᴋ&x&F&F&A&F&0&F&lʟ&x&F&F&B&C&0&C&lᴀ";
        private int customModelData = 1101;
        private List<String> lore = List.of(
            "",
            "&8× &7Po &ckliknięciu &7prawym osoby w &f5 &7kratkach",
            "&8× &7dostają &cSiłę I&7, &8Spowolnienie III &7lub &dRegen V &7na &f10s",
            "",
            "&8× &cPrzedmiot jednorazowy"
        );
        private int radius = 5;
        private int effectDurationSeconds = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCustomModelData() { return customModelData; }
        public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getRadius() { return radius; }
        public void setRadius(int radius) { this.radius = radius; }
        public int getEffectDurationSeconds() { return effectDurationSeconds; }
        public void setEffectDurationSeconds(int effectDurationSeconds) { this.effectDurationSeconds = effectDurationSeconds; }
    }

    public static class MagicCrystalSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&x&B&2&6&6&F&F&lᴍ&x&B&8&7&0&F&F&lᴀ&x&B&E&7&A&F&F&lɢ&x&C&4&8&4&F&F&lɪ&x&C&A&8&E&F&F&lᴄ&x&D&0&9&8&F&F&lᴢ&x&D&6&A&2&F&F&lɴ&x&D&C&A&C&F&F&lʏ&x&E&2&B&6&F&F&l &x&E&8&C&0&F&F&lᴋ&x&E&E&C&A&F&F&lʀ&x&F&4&D&4&F&F&lʏ&x&F&A&D&E&F&F&lꜱ&x&F&F&E&8&F&F&lᴢ&x&F&F&F&2&F&F&lᴛ&x&F&F&F&F&F&F&lᴀ&x&F&F&F&F&F&F&lʟ";
        private int customModelData = 1102;
        private List<String> lore = List.of(
            "",
            "&8× &7Po &ckliknięciu &7prawym dostajesz &e10 złotych serc",
            "",
            "&8× &cPrzedmiot jednorazowy"
        );
        private int goldenHearts = 10;
        private int absorptionDurationSeconds = 120;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCustomModelData() { return customModelData; }
        public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getGoldenHearts() { return goldenHearts; }
        public void setGoldenHearts(int goldenHearts) { this.goldenHearts = goldenHearts; }
        public int getAbsorptionDurationSeconds() { return absorptionDurationSeconds; }
        public void setAbsorptionDurationSeconds(int absorptionDurationSeconds) { this.absorptionDurationSeconds = absorptionDurationSeconds; }
    }

    public static class WuStaffSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&x&F&F&D&9&0&0&lʟ&x&F&F&C&C&0&0&lᴀ&x&F&F&B&F&0&0&lꜱ&x&F&F&B&2&0&0&lᴋ&x&F&F&A&5&0&0&lᴀ&x&F&F&9&8&0&0&l &x&F&F&8&B&0&0&lᴍ&x&F&F&7&E&0&0&lɪ&x&F&F&7&1&0&0&lꜱ&x&F&F&6&4&0&0&lᴛ&x&F&F&5&7&0&0&lʀ&x&F&F&4&A&0&0&lᴀ&x&F&F&3&D&0&0&l &x&F&F&3&0&0&0&lᴡ&x&F&F&2&3&0&0&lᴜ";
        private int customModelData = 1103;
        private List<String> lore = List.of(
            "",
            "&8× &7Uderzenie odpycha gracza &cOdrzutem II",
            "&8× &7Cooldownoldown użycia: &f10s",
            "",
            "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
        );
        private int cooldown = 10;
        private int knockbackLevel = 2;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCustomModelData() { return customModelData; }
        public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getCooldown() { return cooldown; }
        public void setCooldown(int cooldown) { this.cooldown = cooldown; }
        public int getKnockbackLevel() { return knockbackLevel; }
        public void setKnockbackLevel(int knockbackLevel) { this.knockbackLevel = knockbackLevel; }
    }

    public static class WinterHeartSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&x&7&F&D&F&F&F&lꜱ&x&7&0&D&5&F&F&lᴇ&x&6&1&C&B&F&F&lʀ&x&5&2&C&1&F&F&lᴄ&x&4&3&B&7&F&F&lᴇ&x&3&4&A&D&F&F&l &x&2&5&A&3&F&F&lᴢ&x&1&6&9&9&F&F&lɪ&x&0&7&8&F&F&F&lᴍ&x&0&0&8&5&F&F&lʏ";
        private int customModelData = 1104;
        private List<String> lore = List.of(
            "",
            "&8× &7Po użyciu zostajesz &bzamrożony &7na &f4s",
            "&8× &7Nie możesz się ruszać, ale nikt Cię nie bije",
            "&8× &7Możesz normalnie &fjeść",
            "",
            "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
        );
        private int durationSeconds = 4;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCustomModelData() { return customModelData; }
        public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
    }

    public static class DragonFangSettings
    extends OkaeriConfig {
        private boolean enabled = true;
        private String name = "&x&C&8&0&0&F&F&lꜱ&x&C&0&0&0&F&0&lᴍ&x&B&8&0&0&E&1&lᴏ&x&B&0&0&0&D&2&lᴄ&x&A&8&0&0&C&3&lᴢ&x&A&0&0&0&B&4&lʏ&x&9&8&0&0&A&5&l &x&9&0&0&0&9&6&lᴋ&x&8&8&0&0&8&7&lɪ&x&8&0&0&0&7&8&lᴇ&x&7&8&0&0&6&9&lʟ";
        private int customModelData = 1105;
        private List<String> lore = List.of(
            "",
            "&8× &7Po użyciu przez &f4s &7masz &e10% &7szans",
            "&8× &7że uderzony gracz dostanie &fpiorun &7za &c2.5 HP",
            "",
            "&8× &7Przedmiot pochodzi z skrzyni &dmistycznej"
        );
        private int durationSeconds = 4;
        private double lightningChance = 10.0;
        private double lightningDamage = 5.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCustomModelData() { return customModelData; }
        public void setCustomModelData(int customModelData) { this.customModelData = customModelData; }
        public List<String> getLore() { return lore; }
        public void setLore(List<String> lore) { this.lore = lore; }
        public int getDurationSeconds() { return durationSeconds; }
        public void setDurationSeconds(int durationSeconds) { this.durationSeconds = durationSeconds; }
        public double getLightningChance() { return lightningChance; }
        public void setLightningChance(double lightningChance) { this.lightningChance = lightningChance; }
        public double getLightningDamage() { return lightningDamage; }
        public void setLightningDamage(double lightningDamage) { this.lightningDamage = lightningDamage; }
    }
}
