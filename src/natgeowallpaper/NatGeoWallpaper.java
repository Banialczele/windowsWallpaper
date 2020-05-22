package natgeowallpaper;

import com.google.gson.*;
import static com.sun.jna.Library.OPTION_FUNCTION_MAPPER;
import static com.sun.jna.Library.OPTION_TYPE_MAPPER;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.UINT_PTR;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIFunctionMapper;
import com.sun.jna.win32.W32APITypeMapper;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.FilenameFilter;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 *
 * @author marci
 */
public class NatGeoWallpaper {

    static String userDir = System.getProperty("user.home");

    static String photosDir = userDir.concat("/Pictures/NatGeoPaper/");
    static String fetchedImageURL;
    ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
    ZonedDateTime nextRun = now.withHour(9).withMinute(0).withSecond(0);
    static int displayTime = 900000;

    //using FileChooser to select folder we want to get photos from
    static final File dir = new File(photosDir);
    //array of supported extensions
    static final String[] EXTENSIONS = new String[]{"jpg", "jpeg", "png"};

    //filter to identify images by extensions
    static final FilenameFilter IMAGE_FILTER = new FilenameFilter() {
        @Override
        public boolean accept(final File dir, final String name) {
            for (final String ext : EXTENSIONS) {
                if (name.endsWith("." + ext)) {
                    return true;
                }
            }
            return (false);
        }
    };

    public class ScheduledTaskExample {

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        public void startScheduleTask() {
            if (now.compareTo(nextRun) > 0) {
                nextRun = nextRun.plusDays(1);
            }

            Duration duration = Duration.between(now, nextRun);
            long initialDelay = duration.getSeconds();

            final ScheduledFuture<?> taskHandle = scheduler.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    try {
                        fetchedImageURL = getUrl();
                        String path = downloadImage(fetchedImageURL);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
        }
    }

    public void runApp() throws IOException {

        File currDir = new File(System.getProperty("user.dir")).getParentFile();
        BufferedImage popupimg = ImageIO.read(new File(currDir + "\\images\\app.jpg"));
        JPanel panel = new JPanel();
        int trayIconWidth = new TrayIcon(popupimg).getSize().width;
        final PopupMenu popup = new PopupMenu();
        final TrayIcon trayIcon = new TrayIcon(popupimg.getScaledInstance(trayIconWidth, -1, Image.SCALE_SMOOTH), "WallpaperChanger");
        final SystemTray tray = SystemTray.getSystemTray();

//        Create a pop-up menu components
        MenuItem exitItem = new MenuItem("Exit");
        MenuItem customizeTime = new MenuItem("Zmień czas");
        customizeTime.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JFrame frame = new JFrame("Wprowadź czas w milisekundach");
                String value = JOptionPane.showInputDialog(frame, "Podaj wartość w milisekudnach");
                int x = Integer.parseInt(value);
                displayTime = x;
            }
        });

        //Add components to pop-up menu
        popup.add(customizeTime);
        popup.add(exitItem);

        trayIcon.setPopupMenu(popup);

        try {
            tray.add(trayIcon);
        } catch (Exception e) {
            System.out.println("TrayIcon could not be added.");
        }

        exitItem.addActionListener(event -> {
            tray.remove(trayIcon);
            System.exit(0);
        });
        ScheduledTaskExample task = new ScheduledTaskExample();
        task.startScheduleTask();
    }

    public static void main(String[] args) throws Exception {
        NatGeoWallpaper app = new NatGeoWallpaper();
        app.runApp();

        if (dir.isDirectory()) {
            while (true) {
                for (final File f : dir.listFiles(IMAGE_FILTER)) {
                    BufferedImage img = null;
                    try {
                        img = ImageIO.read(f);
                        SPI.INSTANCE.SystemParametersInfo(
                                new UINT_PTR(SPI.SPI_SETDESKWALLPAPER),
                                new UINT_PTR(0),
                                dir + "/" + f.getName(),
                                new UINT_PTR(SPI.SPIF_UPDATEINIFILE | SPI.SPIF_SENDWININICHANGE));
                        Thread.sleep(displayTime);
                    } catch (final IOException e) {
                        throw new Error(e);
                    } catch (InterruptedException ex) {
                        Logger.getLogger(NatGeoWallpaper.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }

    public interface SPI extends StdCallLibrary {

        //from MSDN article
        long SPI_SETDESKWALLPAPER = 20;
        long SPIF_UPDATEINIFILE = 0x01;
        long SPIF_SENDWININICHANGE = 0x02;

        SPI INSTANCE = (SPI) Native.loadLibrary("user32", SPI.class, new HashMap<String, Object>() {
            {
                put(OPTION_TYPE_MAPPER, W32APITypeMapper.UNICODE);
                put(OPTION_FUNCTION_MAPPER, W32APIFunctionMapper.UNICODE);
            }
        });

        boolean SystemParametersInfo(
                UINT_PTR uiAction,
                UINT_PTR uiParam,
                String pvParam,
                UINT_PTR fWinIni
        );
    }

    private static String getUrl() throws Exception {
        String baseUrl = "https://www.nationalgeographic.com/photography/photo-of-the-day/_jcr_content/.gallery.json";

        String strImageUrl = "";
        BufferedReader reader = null;

        // Fetch image url
        System.out.println("Fetching image url...");

        try {
            // Download the JSON file as a String
            URL url = new URL(baseUrl);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder buffer = new StringBuilder();

            int read;
            char[] chars = new char[1024];
            while ((read = reader.read(chars)) != -1) {
                buffer.append(chars, 0, read);
            }
            String json = buffer.toString();
            // Parse the JSON and convert to a Java object
            Gson gson = new Gson();
            Item page = gson.fromJson(json, Item.class);

//             Choose the appropriate image size, and build the url
            strImageUrl = page.items.get(0).image.uri;
            System.out.println("Successfully fetched image url.");
        } catch (UnknownHostException e) {
            final JPanel panel = new JPanel();
            JOptionPane.showMessageDialog(panel, "User does not have internet connection", "User does not have internet connection", JOptionPane.ERROR_MESSAGE);
            throw new Error("User does not have internet connection");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Clean up even if an exception occurs
            if (reader != null) { // BufferedReader
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return strImageUrl;
    }

    private static String downloadImage(String strImageUrl) {
        String filePath = "";
        BufferedImage image = null;

        System.out.println("Downloading image from url...");

        // Ensure that the url exists
        if (!strImageUrl.equals("")) {
            try {
                // Download the image and output to buffer
                URL urlImage = new URL(strImageUrl);
                image = ImageIO.read(urlImage);
                System.out.println("Successfully downloaded image.");

                // Determine file path
                filePath = createFilePath();
                System.out.println("Saving image to: '" + filePath + "'...");
                ImageIO.write(image, "jpg", new File(filePath));

                System.out.println("Successfully saved image.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            final JPanel panel = new JPanel();
            JOptionPane.showMessageDialog(panel, "Error: unable to download image from invalid url.", "Error: unable to download image from invalid url.", JOptionPane.ERROR_MESSAGE);
            System.out.println("Error: unable to download image from invalid url.");
            System.exit(0);
        }
        return filePath;
    }

    /**
     * This method determines the exact file path of the downloaded image
     */
    private static String createFilePath() {
        String homeDir = System.getProperty("user.home");

        // TODO: Path will be different on other OS
        String backgroundDir = homeDir.concat("/Pictures/NatGeoPaper");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        LocalDateTime now = LocalDateTime.now();

        String year = Integer.toString(now.getYear());
        String month = Integer.toString(now.getMonthValue());

        String fileName = dtf.format(now) + ".jpg";

        // Check if the directory exists, and create if necessary
        File directory = new File(backgroundDir);
        if (!directory.exists()) {
            boolean success = directory.mkdirs();
            if (!success) {
                final JPanel panel = new JPanel();
                JOptionPane.showMessageDialog(panel, "Error: unable to create a file.", "Error: unable to create a file.", JOptionPane.ERROR_MESSAGE);

                System.exit(0);
            }
        }
        return backgroundDir + "/" + fileName;
    }

}
