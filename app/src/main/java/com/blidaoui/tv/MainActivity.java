package com.blidaoui.tv;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.SharedPreferences;
import android.view.Gravity;
import android.view.Window;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import android.widget.MediaController;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends android.app.Activity {
    private static final int BG = Color.rgb(16, 20, 25);
    private static final int PANEL = Color.rgb(26, 32, 39);
    private static final int MUTED = Color.rgb(157, 168, 178);
    private static final int ORANGE = Color.rgb(233, 95, 53);
    private static final String PLAYLIST_URL = "https://raw.githubusercontent.com/free-tv/IPTV/master/playlist.m3u8";
    private static final String UPDATE_CHANNEL_ID = "app_updates";
    private TextView syncStatus;
    private TextView playlistStatus;
    private LinearLayout playlistContainer;
    private final List<PlaylistChannel> playlistChannels = new ArrayList<>();
    private String languageCode;
    private long updateDownloadId = -1L;
    private BroadcastReceiver updateReceiver;
    private Uri pendingApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        languageCode = getSharedPreferences("settings", MODE_PRIVATE).getString("language", "ar");
        registerUpdateReceiver();
        setContentView(buildScreen());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 42);
        }
        checkForUpdateNotification();
    }

    @Override
    protected void onDestroy() {
        if (updateReceiver != null) {
            unregisterReceiver(updateReceiver);
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingApk != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls())) {
            Uri apk = pendingApk;
            pendingApk = null;
            installApk(apk);
        }
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(page);

        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(com.blidaoui.tv.R.drawable.ic_tv);
        header.addView(logo, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brand = column();
        brand.setPadding(dp(12), 0, 0, 0);
        brand.addView(label("BLidaoui", 22, Color.WHITE, true));
        brand.addView(label("TV", 13, ORANGE, true));
        brand.addView(label(tr("عالمك على شاشتك", "Your world on screen", "Votre monde à l'écran", "Tu mundo en pantalla", "Ekrandaki dünyan", "Deine Welt auf dem Bildschirm"), 11, MUTED, false));
        brand.addView(label(tr("كشي وجي", "A little of everything", "Un peu de tout", "Un poco de todo", "Her şeyden biraz", "Von allem etwas"), 11, ORANGE, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        Button refresh = actionButton(tr("تحديث التطبيق", "Update app", "Mettre à jour", "Actualizar", "Uygulamayı güncelle", "App aktualisieren"), ORANGE);
        refresh.setOnClickListener(v -> openCustomUpdate());
        header.addView(refresh, new LinearLayout.LayoutParams(dp(132), dp(46)));
        page.addView(header);

        LinearLayout tools = row(Gravity.CENTER_VERTICAL);
        Button languages = actionButton(tr("اللغات", "Languages", "Langues", "Idiomas", "Diller", "Sprachen"), Color.rgb(55, 124, 151));
        languages.setOnClickListener(v -> showLanguages());
        tools.addView(languages, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button login = actionButton(tr("Gmail / Facebook", "Gmail / Facebook", "Gmail / Facebook", "Gmail / Facebook", "Gmail / Facebook", "Gmail / Facebook"), Color.rgb(64, 154, 106));
        login.setOnClickListener(v -> showLoginOptions());
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(0, dp(42), 1);
        loginParams.setMargins(dp(8), 0, 0, 0);
        tools.addView(login, loginParams);
        page.addView(tools, marginTop(16));

        page.addView(quickNavigation(), marginTop(14));

        TextView welcome = label(tr("كل ما تحب مشاهدته\nفي مكان واحد", "Everything you love\nin one place", "Tout ce que vous aimez\nen un seul endroit", "Todo lo que te gusta\nen un solo lugar", "Sevdiğiniz her şey\ntek yerde", "Alles, was du liebst\nan einem Ort"), 30, Color.WHITE, true);
        welcome.setPadding(0, dp(36), 0, dp(8));
        page.addView(welcome);
        page.addView(label(tr("اكتشف آخر محتوى BLidaoui TV واستمتع بتجربة مشاهدة بسيطة وسريعة.", "Discover BLidaoui TV content with a fast, simple viewing experience.", "Découvrez le contenu BLidaoui TV avec une expérience rapide et simple.", "Descubre el contenido de BLidaoui TV de forma rápida y sencilla.", "BLidaoui TV içeriklerini hızlı ve kolay keşfedin.", "Entdecke BLidaoui TV schnell und einfach."), 15, MUTED, false));

        LinearLayout live = panel();
        live.setPadding(dp(16), dp(15), dp(16), dp(15));
        LinearLayout liveText = column();
        liveText.addView(label(tr("الحالة الآن", "Live status", "Statut en direct", "Estado en directo", "Canlı durum", "Live-Status"), 13, MUTED, false));
        syncStatus = label(tr("تم التحديث للتو", "Updated just now", "Mis à jour à l'instant", "Actualizado ahora", "Az önce güncellendi", "Gerade aktualisiert"), 16, Color.WHITE, true);
        liveText.addView(syncStatus);
        live.addView(liveText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView dot = label(tr("● متصل", "● Online", "● En ligne", "● Conectado", "● Çevrimiçi", "● Online"), 13, Color.rgb(88, 204, 137), true);
        live.addView(dot);
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(-1, -2);
        liveParams.topMargin = dp(26);
        page.addView(live, liveParams);

        page.addView(sectionTitle(tr("دليل القنوات المباشر", "Live channel guide", "Guide des chaînes", "Guía de canales", "Canlı kanal rehberi", "Live-Senderguide")), marginTop(30));
        playlistStatus = label(tr("جاري تحميل القنوات...", "Loading channels...", "Chargement des chaînes...", "Cargando canales...", "Kanallar yükleniyor...", "Sender werden geladen..."), 14, MUTED, false);
        page.addView(playlistStatus, marginTop(5));
        Button refreshPlaylist = actionButton(tr("تحديث القنوات", "Refresh channels", "Actualiser les chaînes", "Actualizar canales", "Kanalları yenile", "Sender aktualisieren"), Color.rgb(55, 124, 151));
        refreshPlaylist.setOnClickListener(v -> loadPlaylist());
        page.addView(refreshPlaylist, marginTop(12));
        playlistContainer = column();
        page.addView(playlistContainer, marginTop(12));
        loadPlaylist();

        page.addView(sectionTitle(tr("مباشر الآن", "Live now", "En direct", "En directo", "Şimdi canlı", "Jetzt live")), marginTop(30));
        page.addView(contentCard(tr("الاستوديو الرئيسي", "Main studio", "Studio principal", "Estudio principal", "Ana stüdyo", "Hauptstudio"), tr("بث مباشر • جودة تلقائية", "Live • Auto quality", "Direct • Qualité auto", "En directo • Calidad automática", "Canlı • Otomatik kalite", "Live • Auto-Qualität"), "LIVE", Color.rgb(211, 47, 47)), marginTop(12));
        page.addView(contentCard(tr("أخبار الرياضة", "Sports news", "Actualités sportives", "Noticias deportivas", "Spor haberleri", "Sportnachrichten"), tr("مباشر • آخر المستجدات", "Live • Latest updates", "Direct • Dernières infos", "En directo • Últimas noticias", "Canlı • Son gelişmeler", "Live • Neueste Meldungen"), "LIVE", Color.rgb(55, 124, 151)), marginTop(12));

        page.addView(sectionTitle(tr("المحتوى المميز", "Featured content", "Contenu à la une", "Contenido destacado", "Öne çıkan içerik", "Empfohlene Inhalte")), marginTop(30));
        page.addView(contentCard(tr("حصاد الأسبوع", "Weekly highlights", "Les moments forts", "Lo mejor de la semana", "Haftanın özeti", "Wochenrückblick"), tr("أبرز اللحظات والمواضيع التي تهمك", "Top moments and topics for you", "Les meilleurs moments et sujets", "Los mejores momentos y temas", "Önemli anlar ve konular", "Top-Momente und Themen"), "01", ORANGE), marginTop(12));
        page.addView(contentCard(tr("مباشر من الاستوديو", "Live from the studio", "En direct du studio", "En directo desde el estudio", "Stüdyodan canlı", "Live aus dem Studio"), tr("تابع جديد BLidaoui TV أولاً بأول", "Follow the latest from BLidaoui TV", "Suivez les nouveautés BLidaoui TV", "Sigue las novedades de BLidaoui TV", "BLidaoui TV yeniliklerini takip edin", "Neuigkeiten von BLidaoui TV"), "02", Color.rgb(55, 124, 151)), marginTop(12));

        page.addView(sectionTitle(tr("القنوات المفتوحة", "Free channels", "Chaînes gratuites", "Canales gratuitos", "Ücretsiz kanallar", "Kostenlose Sender")), marginTop(30));
        page.addView(label(tr("قنوات مجانية من دول العالم مرتبة حسب الدولة", "Free channels from around the world by country", "Chaînes gratuites du monde par pays", "Canales gratuitos del mundo por país", "Dünyadan ülkelere göre ücretsiz kanallar", "Kostenlose Sender aus aller Welt nach Land"), 14, MUTED, false), marginTop(5));
        page.addView(countryCard("الجزائر", "ENTV • Canal Algérie • El Bilad", "01", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("المغرب", "Al Aoula • 2M • Medi 1 TV", "02", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("مصر", "CBC • ON • الحياة", "03", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("السعودية", "Saudi TV • SBC • الإخبارية", "04", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("فرنسا", "France 2 • TF1 • M6", "05", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("الولايات المتحدة", "ABC • CBS • NBC • PBS", "06", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("المملكة المتحدة", "BBC • ITV • Channel 4", "07", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("ألمانيا", "ARD • ZDF • DW", "08", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("إيطاليا", "RAI 1 • RAI 2 • Mediaset", "09", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("إسبانيا", "TVE • Antena 3 • Telecinco", "10", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("تركيا", "TRT 1 • ATV • Show TV", "11", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("الهند", "DD National • Star Utsav • Sony", "12", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("اليابان", "NHK • TBS • TV Tokyo", "13", Color.rgb(64, 154, 106), false), marginTop(12));
        page.addView(countryCard("البرازيل", "TV Brasil • Globo • SBT", "14", Color.rgb(64, 154, 106), false), marginTop(12));

        page.addView(sectionTitle(tr("القنوات المشفرة", "Premium channels", "Chaînes premium", "Canales premium", "Premium kanallar", "Premium-Sender")), marginTop(30));
        page.addView(label(tr("باقات وقنوات مدفوعة مرتبة حسب الدولة", "Paid packages and channels by country", "Bouquets et chaînes payantes par pays", "Paquetes y canales de pago por país", "Ülkelere göre ücretli paketler ve kanallar", "Bezahlpakete und Sender nach Land"), 14, MUTED, false), marginTop(5));
        page.addView(countryCard("الجزائر", "باقة رياضية • أفلام • ترفيه", "01", ORANGE, true), marginTop(12));
        page.addView(countryCard("المغرب", "باقة رياضية • سينما • أطفال", "02", ORANGE, true), marginTop(12));
        page.addView(countryCard("قطر", "beIN Sports • beIN Movies", "03", ORANGE, true), marginTop(12));
        page.addView(countryCard("الإمارات", "OSN • STARZPLAY • أبوظبي الرياضية", "04", ORANGE, true), marginTop(12));
        page.addView(countryCard("فرنسا", "Canal+ • Eurosport • RMC Sport", "05", ORANGE, true), marginTop(12));
        page.addView(countryCard("الولايات المتحدة", "HBO • Showtime • ESPN", "06", ORANGE, true), marginTop(12));
        page.addView(countryCard("المملكة المتحدة", "Sky • BT Sport • Premier Sports", "07", ORANGE, true), marginTop(12));
        page.addView(countryCard("ألمانيا", "Sky Deutschland • DAZN", "08", ORANGE, true), marginTop(12));
        page.addView(countryCard("إيطاليا", "Sky Italia • DAZN Italia", "09", ORANGE, true), marginTop(12));
        page.addView(countryCard("إسبانيا", "Movistar+ • DAZN España", "10", ORANGE, true), marginTop(12));
        page.addView(countryCard("تركيا", "Digiturk • S Sport", "11", ORANGE, true), marginTop(12));
        page.addView(countryCard("الهند", "Tata Play • SonyLIV • ZEE5", "12", ORANGE, true), marginTop(12));
        page.addView(countryCard("اليابان", "WOWOW • J SPORTS", "13", ORANGE, true), marginTop(12));
        page.addView(countryCard("البرازيل", "Premiere • SporTV • Max", "14", ORANGE, true), marginTop(12));

        page.addView(sectionTitle(tr("القنوات الرياضية", "Sports channels", "Chaînes sportives", "Canales deportivos", "Spor kanalları", "Sportsender")), marginTop(30));
        page.addView(label(tr("أخبار الرياضة والمباريات والبطولات العالمية", "Sports news, matches and world tournaments", "Actualités, matchs et compétitions mondiales", "Noticias, partidos y torneos mundiales", "Spor haberleri, maçlar ve dünya turnuvaları", "Sportnachrichten, Spiele und Welttourniere"), 14, MUTED, false), marginTop(5));
        page.addView(sectionTitle(tr("رياضة مفتوحة", "Free sports", "Sport gratuit", "Deporte gratuito", "Ücretsiz spor", "Kostenloser Sport")), marginTop(18));
        page.addView(countryCard("الجزائر", "TV6 • Programme National الرياضي", "01", Color.rgb(55, 124, 151), false), marginTop(12));
        page.addView(countryCard("المغرب", "Arryadia • الرياضية المغربية", "02", Color.rgb(55, 124, 151), false), marginTop(12));
        page.addView(countryCard("السعودية", "KSA Sport • SSC المفتوحة", "03", Color.rgb(55, 124, 151), false), marginTop(12));
        page.addView(countryCard("فرنسا", "L'Équipe • France 3 Sport", "04", Color.rgb(55, 124, 151), false), marginTop(12));
        page.addView(countryCard("ألمانيا", "Sport1 • ARD Sport", "05", Color.rgb(55, 124, 151), false), marginTop(12));
        page.addView(sectionTitle("beIN SPORTS"), marginTop(18));
        page.addView(label(tr("قنوات رياضية مشفرة ضمن باقة beIN SPORTS", "Encrypted sports channels in the beIN SPORTS package", "Chaînes sportives cryptées du bouquet beIN SPORTS", "Canales deportivos codificados de beIN SPORTS", "beIN SPORTS paketindeki şifreli spor kanalları", "Verschlüsselte Sportsender im beIN SPORTS-Paket"), 14, MUTED, false), marginTop(5));
        page.addView(countryCard("beIN SPORTS 1 - 9", "مباريات كرة القدم والبطولات العالمية", "01", ORANGE, true), marginTop(12));
        page.addView(countryCard("beIN SPORTS MAX", "بطولات إضافية ومباريات مباشرة", "02", ORANGE, true), marginTop(12));
        page.addView(countryCard("beIN SPORTS NEWS", "أخبار وتحليلات رياضية على مدار الساعة", "03", ORANGE, true), marginTop(12));
        page.addView(countryCard("beIN SPORTS 4K", "بث رياضي بدقة فائقة عند توفر الاشتراك", "04", ORANGE, true), marginTop(12));
        page.addView(sectionTitle(tr("باقات رياضية مشفرة", "Encrypted sports packages", "Bouquets sportifs cryptés", "Paquetes deportivos codificados", "Şifreli spor paketleri", "Verschlüsselte Sportpakete")), marginTop(18));
        page.addView(countryCard("قطر", "beIN Sports • beIN MAX", "01", ORANGE, true), marginTop(12));
        page.addView(countryCard("الإمارات", "أبوظبي الرياضية بريميوم • STARZPLAY Sports", "02", ORANGE, true), marginTop(12));
        page.addView(countryCard("المملكة المتحدة", "Sky Sports • TNT Sports", "03", ORANGE, true), marginTop(12));
        page.addView(countryCard("الولايات المتحدة", "ESPN • Fox Sports • NBC Sports", "04", ORANGE, true), marginTop(12));
        page.addView(countryCard("إسبانيا", "Movistar Deportes • DAZN", "05", ORANGE, true), marginTop(12));

        page.addView(sectionTitle(tr("دول العالم", "World countries", "Pays du monde", "Países del mundo", "Dünya ülkeleri", "Länder der Welt")), marginTop(30));
        page.addView(label(tr("اختر الدولة لاستعراض قنواتها حسب النوع", "Choose a country to browse its channels", "Choisissez un pays pour parcourir ses chaînes", "Elige un país para ver sus canales", "Kanallara göz atmak için ülke seçin", "Wähle ein Land für seine Sender"), 14, MUTED, false), marginTop(5));
        page.addView(countryDirectoryCard("🇩🇿", "الجزائر", "ENTV • Canal Algérie • El Bilad • TV6"), marginTop(12));
        page.addView(countryDirectoryCard("🇲🇦", "المغرب", "Al Aoula • 2M • Arryadia • Medi 1 TV"), marginTop(12));
        page.addView(countryDirectoryCard("🇪🇬", "مصر", "CBC • ON • الحياة • DMC"), marginTop(12));
        page.addView(countryDirectoryCard("🇸🇦", "السعودية", "Saudi TV • SBC • الإخبارية • SSC"), marginTop(12));
        page.addView(countryDirectoryCard("🇫🇷", "فرنسا", "France 2 • TF1 • M6 • L'Équipe"), marginTop(12));
        page.addView(countryDirectoryCard("🇬🇧", "المملكة المتحدة", "BBC • ITV • Channel 4 • Sky Sports"), marginTop(12));
        page.addView(countryDirectoryCard("🇺🇸", "الولايات المتحدة", "ABC • CBS • NBC • ESPN"), marginTop(12));
        page.addView(countryDirectoryCard("🇩🇪", "ألمانيا", "ARD • ZDF • DW • Sport1"), marginTop(12));

        page.addView(sectionTitle("YouTube"), marginTop(30));
        page.addView(contentCard(tr("قنوات BLidaoui TV", "BLidaoui TV channels", "Chaînes BLidaoui TV", "Canales BLidaoui TV", "BLidaoui TV kanalları", "BLidaoui TV-Sender"), tr("آخر الفيديوهات والمحتوى الرسمي", "Latest videos and official content", "Dernières vidéos et contenu officiel", "Últimos vídeos y contenido oficial", "Son videolar ve resmi içerik", "Neueste Videos und offizielle Inhalte"), "YT", Color.rgb(211, 47, 47)), marginTop(12));

        page.addView(sectionTitle(tr("الدردشة", "Chat", "Discussion", "Chat", "Sohbet", "Chat")), marginTop(30));
        page.addView(contentCard(tr("غرفة المشاهدة", "Watch room", "Salon de visionnage", "Sala de visualización", "İzleme odası", "Watchroom"), tr("تحدث مع المشاهدين وشارك رأيك", "Talk with viewers and share your opinion", "Discutez avec les spectateurs", "Habla con los espectadores y comparte tu opinión", "İzleyicilerle konuşun ve fikrinizi paylaşın", "Sprich mit Zuschauern und teile deine Meinung"), "CHAT", Color.rgb(119, 88, 164)), marginTop(12));

        TextView footer = label(tr("BLidaoui TV  •  الإصدار 1.0", "BLidaoui TV  •  Version 1.0", "BLidaoui TV  •  Version 1.0", "BLidaoui TV  •  Versión 1.0", "BLidaoui TV  •  Sürüm 1.0", "BLidaoui TV  •  Version 1.0"), 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        page.addView(footer, marginTop(32));
        page.postDelayed(() -> animatePage(page), 80);
        return scroll;
    }

    private LinearLayout contentCard(String title, String subtitle, String number, int accent) {
        LinearLayout card = panel();
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView badge = label(number, 18, accent, true);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(46)));
        LinearLayout texts = column();
        texts.setPadding(dp(15), 0, 0, 0);
        texts.addView(label(title, 18, Color.WHITE, true));
        texts.addView(label(subtitle, 13, MUTED, false));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = label("‹", 28, Color.WHITE, false);
        card.addView(arrow);
        card.setOnClickListener(v -> {
            if (title.contains("BLidaoui TV")) {
                openYouTube();
            } else {
                showChatDialog();
            }
        });
        return card;
    }

    private void loadPlaylist() {
        playlistStatus.setText(tr("جاري تحميل القنوات...", "Loading channels...", "Chargement des chaînes...", "Cargando canales...", "Kanallar yükleniyor...", "Sender werden geladen..."));
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(PLAYLIST_URL).openConnection();
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("User-Agent", "BLidaoui-TV/1.0");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IllegalStateException("Playlist unavailable");
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                List<PlaylistChannel> channels = parsePlaylist(reader);
                reader.close();
                new Handler(Looper.getMainLooper()).post(() -> showPlaylist(channels));
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> playlistStatus.setText(tr("تعذر تحميل قائمة القنوات", "Could not load channel list", "Impossible de charger les chaînes", "No se pudo cargar la lista", "Kanal listesi yüklenemedi", "Senderliste konnte nicht geladen werden")));
            }
        }).start();
    }

    private List<PlaylistChannel> parsePlaylist(BufferedReader reader) throws Exception {
        List<PlaylistChannel> channels = new ArrayList<>();
        String line;
        String name = null;
        String group = "Other";
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF")) {
                int comma = line.indexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "Unnamed channel";
                group = attribute(line, "group-title");
                if (group.isEmpty()) group = "Other";
                String country = attribute(line, "tvg-country");
                if (!country.isEmpty()) group = country;
            } else if (name != null && !line.isEmpty() && !line.startsWith("#")) {
                if (line.startsWith("https://")) channels.add(new PlaylistChannel(name, group, line));
                name = null;
            }
        }
        return channels;
    }

    private String attribute(String line, String key) {
        String marker = key + "=\"";
        int start = line.indexOf(marker);
        if (start < 0) return "";
        start += marker.length();
        int end = line.indexOf('"', start);
        return end > start ? line.substring(start, end) : "";
    }

    private void showPlaylist(List<PlaylistChannel> channels) {
        playlistChannels.clear();
        playlistChannels.addAll(channels);
        playlistContainer.removeAllViews();
        Map<String, List<PlaylistChannel>> groups = new LinkedHashMap<>();
        for (PlaylistChannel channel : channels) groups.computeIfAbsent(channel.group, key -> new ArrayList<>()).add(channel);
        playlistStatus.setText(tr("تم تحميل ", "Loaded ", "Chargé : ", "Cargados: ", "Yüklendi: ", "Geladen: ") + channels.size() + tr(" قناة من المصدر الرسمي", " channels from the official source", " chaînes depuis la source officielle", " canales desde la fuente oficial", " kanal resmi kaynaktan", " Sender aus der offiziellen Quelle"));
        int index = 1;
        for (Map.Entry<String, List<PlaylistChannel>> entry : groups.entrySet()) {
            playlistContainer.addView(playlistGroupCard(entry.getKey(), entry.getValue(), String.valueOf(index++)), marginTop(8));
        }
    }

    private LinearLayout playlistGroupCard(String group, List<PlaylistChannel> channels, String number) {
        LinearLayout card = panel();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView badge = label(number, 15, ORANGE, true);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout details = column();
        details.setPadding(dp(12), 0, 0, 0);
        details.addView(label(countryFlag(group) + "  " + group, 16, Color.WHITE, true));
        details.addView(label(channels.size() + " " + tr("قناة", "channels", "chaînes", "canales", "kanal", "Sender"), 12, MUTED, false));
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(label(tr("عرض ›", "View ›", "Voir ›", "Ver ›", "Görüntüle ›", "Ansehen ›"), 13, ORANGE, true));
        card.setOnClickListener(v -> showChannelList(group, channels));
        return card;
    }

    private void showChannelList(String group, List<PlaylistChannel> channels) {
        String[] names = new String[channels.size()];
        for (int index = 0; index < channels.size(); index++) names[index] = channels.get(index).name;
        new AlertDialog.Builder(this).setTitle(group).setItems(names, (dialog, which) -> openStream(channels.get(which))).setNegativeButton(tr("إغلاق", "Close", "Fermer", "Cerrar", "Kapat", "Schließen"), null).show();
    }

    private void openStream(PlaylistChannel channel) {
        if (!channel.url.startsWith("https://")) {
            Toast.makeText(this, tr("تم رفض الرابط غير الآمن", "Unsafe stream rejected", "Flux non sécurisé refusé", "Flujo no seguro rechazado", "Güvensiz yayın reddedildi", "Unsicherer Stream abgelehnt"), Toast.LENGTH_LONG).show();
            return;
        }
        Dialog playerDialog = new Dialog(this);
        playerDialog.setTitle(channel.name);
        LinearLayout playerLayout = column();
        playerLayout.setPadding(dp(12), dp(12), dp(12), dp(12));
        VideoView video = new VideoView(this);
        video.setVideoURI(Uri.parse(channel.url));
        MediaController controls = new MediaController(this);
        controls.setAnchorView(video);
        video.setMediaController(controls);
        playerLayout.addView(video, new LinearLayout.LayoutParams(-1, dp(230)));
        TextView title = label(channelFlagLine(channel) + "\n" + channel.name, 15, Color.WHITE, true);
        title.setPadding(0, dp(10), 0, 0);
        playerLayout.addView(title);
        playerDialog.setContentView(playerLayout);
        Window window = playerDialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);
        playerDialog.show();
        video.setOnPreparedListener(mediaPlayer -> video.start());
        video.setOnErrorListener((view, what, extra) -> {
            Toast.makeText(this, tr("تعذر تشغيل هذه القناة", "This channel could not be played", "Impossible de lire cette chaîne", "No se pudo reproducir este canal", "Bu kanal oynatılamadı", "Dieser Sender konnte nicht abgespielt werden"), Toast.LENGTH_LONG).show();
            return false;
        });
    }

    private String channelFlagLine(PlaylistChannel channel) {
        return countryFlag(channel.group) + "  " + channel.group;
    }

    private String countryFlag(String country) {
        String key = country.toLowerCase();
        if (key.contains("alger") || key.contains("dz") || key.contains("الجزائر")) return "🇩🇿";
        if (key.contains("morocc") || key.contains("ma") || key.contains("المغرب")) return "🇲🇦";
        if (key.contains("egypt") || key.contains("eg") || key.contains("مصر")) return "🇪🇬";
        if (key.contains("saudi") || key.contains("sa") || key.contains("السعودية")) return "🇸🇦";
        if (key.contains("france") || key.contains("fr") || key.contains("فرنسا")) return "🇫🇷";
        if (key.contains("united kingdom") || key.contains("uk") || key.contains("england")) return "🇬🇧";
        if (key.contains("united states") || key.contains("usa") || key.contains("us")) return "🇺🇸";
        if (key.contains("germany") || key.contains("de") || key.contains("ألمانيا")) return "🇩🇪";
        if (key.contains("italy") || key.contains("it") || key.contains("إيطاليا")) return "🇮🇹";
        if (key.contains("spain") || key.contains("es") || key.contains("إسبانيا")) return "🇪🇸";
        if (key.contains("turkey") || key.contains("tr") || key.contains("تركيا")) return "🇹🇷";
        if (key.contains("india") || key.contains("in") || key.contains("الهند")) return "🇮🇳";
        if (key.contains("japan") || key.contains("jp") || key.contains("اليابان")) return "🇯🇵";
        if (key.contains("brazil") || key.contains("br") || key.contains("البرازيل")) return "🇧🇷";
        if (key.contains("qatar") || key.contains("qa") || key.contains("قطر")) return "🇶🇦";
        if (key.contains("uae") || key.contains("emirates") || key.contains("الإمارات")) return "🇦🇪";
        return "🌐";
    }

    private static class PlaylistChannel {
        final String name;
        final String group;
        final String url;

        PlaylistChannel(String name, String group, String url) {
            this.name = name;
            this.group = group;
            this.url = url;
        }
    }

    private HorizontalScrollView quickNavigation() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout navigation = row(Gravity.CENTER_VERTICAL);
        navigation.addView(navButton(tr("الرئيسية", "Home", "Accueil", "Inicio", "Ana sayfa", "Startseite")), new LinearLayout.LayoutParams(dp(104), dp(40)));
        navigation.addView(navButton(tr("الرياضة", "Sports", "Sports", "Deportes", "Spor", "Sport")), navParams());
        navigation.addView(navButton(tr("الدول", "Countries", "Pays", "Países", "Ülkeler", "Länder")), navParams());
        navigation.addView(navButton(tr("المفضلة", "Favorites", "Favoris", "Favoritos", "Favoriler", "Favoriten")), navParams());
        scroll.addView(navigation);
        return scroll;
    }

    private Button navButton(String text) {
        Button button = actionButton(text, PANEL);
        button.setOnClickListener(v -> Toast.makeText(this, tr("تم اختيار ", "Selected: ", "Sélectionné : ", "Seleccionado: ", "Seçildi: ", "Ausgewählt: ") + text, Toast.LENGTH_SHORT).show());
        return button;
    }

    private LinearLayout.LayoutParams navParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(104), dp(40));
        params.setMargins(dp(8), 0, 0, 0);
        return params;
    }

    private LinearLayout countryDirectoryCard(String flag, String country, String channels) {
        LinearLayout card = panel();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView flagView = label(flag, 27, Color.WHITE, false);
        flagView.setGravity(Gravity.CENTER);
        card.addView(flagView, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout details = column();
        details.setPadding(dp(14), 0, 0, 0);
        details.addView(label(country, 17, Color.WHITE, true));
        details.addView(label(channels, 12, MUTED, false));
        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(label(tr("عرض ›", "View ›", "Voir ›", "Ver ›", "Görüntüle ›", "Ansehen ›"), 13, ORANGE, true));
        card.setOnClickListener(v -> Toast.makeText(this, tr("تم فتح قنوات ", "Opened channels for ", "Chaînes ouvertes pour ", "Canales abiertas para ", "Kanallar açıldı: ", "Sender geöffnet: ") + country, Toast.LENGTH_SHORT).show());
        return card;
    }

    private void animatePage(LinearLayout page) {
        page.setAlpha(0f);
        page.setTranslationY(dp(18));
        page.animate().alpha(1f).translationY(0f).setDuration(450).start();
    }

    private void showLanguages() {
        String[] languages = {"العربية", "English", "Français", "Español", "Türkçe", "Deutsch"};
        String[] codes = {"ar", "en", "fr", "es", "tr", "de"};
        new AlertDialog.Builder(this).setTitle(tr("اختر اللغة", "Choose language", "Choisir la langue", "Elegir idioma", "Dil seçin", "Sprache wählen")).setItems(languages,
                (dialog, which) -> {
                    languageCode = codes[which];
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putString("language", languageCode).apply();
                    recreate();
                }).show();
    }

    private void showLoginOptions() {
        new AlertDialog.Builder(this).setTitle(tr("تسجيل الدخول", "Sign in", "Connexion", "Iniciar sesión", "Giriş yap", "Anmelden"))
            .setMessage(tr("اختر طريقة الدخول. يلزم إعداد OAuth الرسمي لتفعيل الحسابات.", "Choose a sign-in method. Official OAuth setup is required.", "Choisissez une méthode. OAuth officiel requis.", "Elige un método. Se requiere OAuth oficial.", "Giriş yöntemi seçin. Resmi OAuth kurulumu gerekir.", "Wähle eine Methode. Offizielles OAuth ist erforderlich."))
            .setPositiveButton("Google", (dialog, which) -> Toast.makeText(this, tr("Google OAuth غير مفعّل بعد", "Google OAuth is not enabled yet", "OAuth Google n'est pas activé", "OAuth de Google aún no está activo", "Google OAuth henüz etkin değil", "Google OAuth ist noch nicht aktiviert"), Toast.LENGTH_SHORT).show())
            .setNegativeButton("Facebook", (dialog, which) -> Toast.makeText(this, tr("Facebook OAuth غير مفعّل بعد", "Facebook OAuth is not enabled yet", "OAuth Facebook n'est pas activé", "OAuth de Facebook aún no está activo", "Facebook OAuth henüz etkin değil", "Facebook OAuth ist noch nicht aktiviert"), Toast.LENGTH_SHORT).show())
            .setNeutralButton(tr("إلغاء", "Cancel", "Annuler", "Cancelar", "İptal", "Abbrechen"), null).show();
    }

    private void showChatDialog() {
        EditText message = new EditText(this);
        message.setHint(tr("اكتب رسالتك", "Write your message", "Écrivez votre message", "Escribe tu mensaje", "Mesajınızı yazın", "Nachricht schreiben"));
        message.setSingleLine(false);
        new AlertDialog.Builder(this).setTitle(tr("غرفة المشاهدة", "Watch room", "Salon de visionnage", "Sala de visualización", "İzleme odası", "Watchroom")).setView(message)
            .setPositiveButton(tr("إرسال", "Send", "Envoyer", "Enviar", "Gönder", "Senden"), (dialog, which) -> Toast.makeText(this, tr("تم تجهيز الرسالة للدردشة", "Message ready for chat", "Message prêt pour la discussion", "Mensaje listo para el chat", "Mesaj sohbete hazır", "Nachricht für den Chat bereit"), Toast.LENGTH_SHORT).show())
            .setNegativeButton(tr("إغلاق", "Close", "Fermer", "Cerrar", "Kapat", "Schließen"), null).show();
    }

    private void openYouTube() {
        Uri uri = Uri.parse("https://www.youtube.com/@BLidaouiTV");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, tr("تعذر فتح YouTube", "Could not open YouTube", "Impossible d'ouvrir YouTube", "No se pudo abrir YouTube", "YouTube açılamadı", "YouTube konnte nicht geöffnet werden"), Toast.LENGTH_SHORT).show();
        }
    }

    private String tr(String arabic, String english, String french, String spanish, String turkish, String german) {
        switch (languageCode) {
            case "en": return english;
            case "fr": return french;
            case "es": return spanish;
            case "tr": return turkish;
            case "de": return german;
            default: return arabic;
        }
    }

    private LinearLayout countryCard(String country, String channels, String number, int accent, boolean encrypted) {
        LinearLayout card = panel();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView badge = label(number, 16, accent, true);
        badge.setGravity(Gravity.CENTER);
        card.addView(badge, new LinearLayout.LayoutParams(dp(42), dp(42)));

        LinearLayout texts = column();
        texts.setPadding(dp(14), 0, 0, 0);
        texts.addView(label(country, 17, Color.WHITE, true));
        texts.addView(label(channels, 12, MUTED, false));
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView access = label((encrypted ? tr("مشفرة", "Premium", "Premium", "Premium", "Premium", "Premium") : tr("مفتوحة", "Free", "Gratuite", "Gratis", "Ücretsiz", "Kostenlos")) + "\n" + tr("فتح الرابط", "Open source", "Ouvrir la source", "Abrir fuente", "Kaynağı aç", "Quelle öffnen"), 11, accent, true);
        access.setGravity(Gravity.CENTER);
        card.addView(access, new LinearLayout.LayoutParams(dp(58), dp(34)));
        card.setContentDescription(tr("فتح مصدر ", "Open source for ", "Ouvrir la source de ", "Abrir fuente de ", "Kaynağı aç: ", "Quelle öffnen: ") + country);
        card.setOnClickListener(v -> showQualityOptions(country, channels));
        return card;
    }

    private void openCustomUpdate() {
        Toast.makeText(this, tr("جاري البحث عن تحديث...", "Checking for updates...", "Recherche de mises à jour...", "Buscando actualizaciones...", "Güncellemeler kontrol ediliyor...", "Suche nach Updates..."), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                URL api = new URL("https://api.github.com/repos/blidaouii/Blidaoui-TV/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) api.openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("Update check failed");
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                JSONObject release = new JSONObject(response.toString());
                String latestVersion = release.optString("tag_name", "").replace("v", "");
                JSONArray assets = release.optJSONArray("assets");
                String apkUrl = null;
                if (assets != null) {
                    for (int index = 0; index < assets.length(); index++) {
                        JSONObject asset = assets.getJSONObject(index);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                final String downloadUrl = apkUrl;
                new Handler(Looper.getMainLooper()).post(() -> handleUpdateResult(latestVersion, downloadUrl));
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, tr("تعذر فحص التحديثات", "Could not check for updates", "Impossible de vérifier les mises à jour", "No se pudieron comprobar las actualizaciones", "Güncellemeler kontrol edilemedi", "Updates konnten nicht geprüft werden"), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void checkForUpdateNotification() {
        new Thread(() -> {
            try {
                URL api = new URL("https://api.github.com/repos/blidaouii/Blidaoui-TV/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) api.openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                JSONObject release = new JSONObject(response.toString());
                String latest = release.optString("tag_name", "").replace("v", "");
                String notified = getSharedPreferences("settings", MODE_PRIVATE).getString("notified_release", "");
                String current = "1.0.0";
                try {
                    current = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (Exception ignored) {
                }
                if (!latest.isEmpty() && isNewerVersion(latest, current) && !latest.equals(notified)) {
                    getSharedPreferences("settings", MODE_PRIVATE).edit().putString("notified_release", latest).apply();
                    new Handler(Looper.getMainLooper()).post(() -> notifyUpdate(latest));
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private void notifyUpdate(String version) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(UPDATE_CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, UPDATE_CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(com.blidaoui.tv.R.drawable.ic_tv)
                .setContentTitle(tr("تحديث جديد متاح", "New update available", "Nouvelle mise à jour", "Nueva actualización", "Yeni güncelleme", "Neues Update"))
                .setContentText(tr("الإصدار ", "Version ", "Version ", "Versión ", "Sürüm ", "Version ") + version)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        try {
            manager.notify(1001, builder.build());
        } catch (SecurityException ignored) {
        }
    }

    private void handleUpdateResult(String latestVersion, String apkUrl) {
        String currentVersion = "1.0.0";
        try {
            currentVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        if (latestVersion.isEmpty() || !isNewerVersion(latestVersion, currentVersion)) {
            Toast.makeText(this, tr("التطبيق محدث بالفعل", "The app is up to date", "L'application est à jour", "La aplicación está actualizada", "Uygulama güncel", "Die App ist aktuell"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (apkUrl == null || !apkUrl.startsWith("https://github.com/")) {
            Toast.makeText(this, tr("لا يوجد ملف APK للتحديث", "No APK is available", "Aucun APK disponible", "No hay APK disponible", "APK bulunamadı", "Keine APK verfügbar"), Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(tr("تحديث جديد", "New update", "Nouvelle mise à jour", "Nueva actualización", "Yeni güncelleme", "Neues Update"))
                .setMessage(tr("يتوفر الإصدار ", "Version ", "Version ", "Versión ", "Sürüm ", "Version ") + latestVersion)
                .setPositiveButton(tr("تنزيل", "Download", "Télécharger", "Descargar", "İndir", "Herunterladen"), (dialog, which) -> downloadUpdate(apkUrl))
                .setNegativeButton(tr("لاحقاً", "Later", "Plus tard", "Más tarde", "Daha sonra", "Später"), null)
                .show();
    }

    private boolean isNewerVersion(String latest, String current) {
        try {
            String[] latestParts = latest.split("\\.");
            String[] currentParts = current.split("\\.");
            int length = Math.max(latestParts.length, currentParts.length);
            for (int index = 0; index < length; index++) {
                int latestPart = index < latestParts.length ? Integer.parseInt(latestParts[index]) : 0;
                int currentPart = index < currentParts.length ? Integer.parseInt(currentParts[index]) : 0;
                if (latestPart != currentPart) return latestPart > currentPart;
            }
        } catch (NumberFormatException ignored) {
            return !latest.equals(current);
        }
        return false;
    }

    private void downloadUpdate(String apkUrl) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
        request.setTitle("BLidaoui TV update");
        request.setDescription(tr("جاري تنزيل التحديث", "Downloading update", "Téléchargement", "Descargando actualización", "Güncelleme indiriliyor", "Update wird heruntergeladen"));
        request.setMimeType("application/vnd.android.package-archive");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(this, "Download", "BLidaoui-TV-update.apk");
        updateDownloadId = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
        Toast.makeText(this, tr("بدأ تنزيل التحديث", "Update download started", "Téléchargement commencé", "Descarga iniciada", "İndirme başladı", "Download gestartet"), Toast.LENGTH_SHORT).show();
    }

    private void registerUpdateReceiver() {
        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction()) && intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) == updateDownloadId) {
                    Uri apk = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).getUriForDownloadedFile(updateDownloadId);
                    if (apk != null) installUpdate(apk);
                }
            }
        };
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) registerReceiver(updateReceiver, filter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(updateReceiver, filter);
    }

    private void installUpdate(Uri apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            pendingApk = apk;
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, tr("اسمح بالتثبيت ثم اضغط على ملف التحديث", "Allow installation, then open the update file", "Autorisez l'installation puis ouvrez le fichier", "Permite la instalación y abre el archivo", "Kuruluma izin verin ve dosyayı açın", "Installation erlauben und Datei öffnen"), Toast.LENGTH_LONG).show();
            return;
        }
        installApk(apk);
    }

    private void installApk(Uri apk) {
        Intent install = new Intent(Intent.ACTION_VIEW, apk);
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(install);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, tr("تعذر فتح مثبت التطبيقات", "Could not open package installer", "Impossible d'ouvrir l'installateur", "No se pudo abrir el instalador", "Paket yükleyici açılamadı", "Paketinstaller konnte nicht geöffnet werden"), Toast.LENGTH_LONG).show();
        }
    }

    private void showQualityOptions(String country, String channels) {
        String[] qualities = {tr("تلقائي", "Auto", "Auto", "Automático", "Otomatik", "Automatisch"), "SD - 480p", "HD - 720p", "Full HD - 1080p", "4K - 2160p"};
        new AlertDialog.Builder(this)
            .setTitle(tr("اختر جودة المشاهدة", "Choose viewing quality", "Choisir la qualité", "Elegir calidad", "Görüntü kalitesi seçin", "Wiedergabequalität wählen"))
                .setItems(qualities, (dialog, which) -> openChannelSearch(country, channels, qualities[which]))
                .setNegativeButton("إلغاء", null)
                .show();
    }

    private void openChannelSearch(String country, String channels, String quality) {
        Toast.makeText(this, tr("الجودة: ", "Quality: ", "Qualité : ", "Calidad: ", "Kalite: ", "Qualität: ") + quality + tr(" - أضف مصدر بث رسمي لتشغيل القناة", " - Add an official stream source to play", " - Ajoutez une source officielle", " - Añade una fuente oficial", " - Oynatmak için resmi kaynak ekleyin", " - Offizielle Quelle hinzufügen"), Toast.LENGTH_LONG).show();
    }

    private TextView sectionTitle(String text) {
        return label(text, 20, Color.WHITE, true);
    }

    private Button actionButton(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        return button;
    }

    private LinearLayout panel() {
        LinearLayout view = row(Gravity.CENTER_VERTICAL);
        view.setBackgroundColor(PANEL);
        return view;
    }

    private LinearLayout row(int gravity) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(gravity);
        return view;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setTextDirection(View.TEXT_DIRECTION_ANY_RTL);
        return view;
    }

    private LinearLayout.LayoutParams marginTop(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(value);
        return params;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}