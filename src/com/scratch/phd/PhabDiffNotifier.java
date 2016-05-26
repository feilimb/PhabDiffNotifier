package com.scratch.phd;

import java.awt.AWTException;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.libgrowl.Application;
import net.sf.libgrowl.GrowlConnector;
import net.sf.libgrowl.Notification;
import net.sf.libgrowl.NotificationType;
import net.sf.libgrowl.internal.IProtocol;

public class PhabDiffNotifier
{
   private String PHABRICATOR_HOST_URL = null;

   private String PHABRICATOR_USER_ID = null;
   
   private String CONDUIT_API_TOKEN = null;

   private String CONDUIT_CERTIFICATE = null;

   private int REPEAT_DELAY_SECONDS = 180;

   private static final int MIN_REPEAT_DELAY_SECONDS = 60;
   
   private static final int QUERY_LAST_NUM_ENTRIES = 3;

   private static final String APPLICATION_NAME = "PhabDiffNotifier";
   
   private static final SimpleDateFormat FMT_DATE_AND_TIME = new SimpleDateFormat("MM/dd/yyyy @ HH:mm");

   private GrowlConnector growl;

   private Application application;

   private NotificationType notificationType;

   private boolean isPawsed;
   
   private boolean sleepMode = true;

   public static void main(String[] args)
   {
      PhabDiffNotifier f = new PhabDiffNotifier();
      String configPropertiesPath = "./";
      if (args.length > 0)
      {
         configPropertiesPath = args[0];
      }
      f.initialiseProperties(configPropertiesPath);
      f.initialiseGrowl();
      f.initialiseSysTray();
      f.start();
   }

   private void initialiseProperties(String configPropertiesPath)
   {
      Properties props = new Properties();
      try
      {
         //props.load(PhabDiffNotifier.class.getResourceAsStream("/config.properties"));
         props.load(new FileInputStream(new File(configPropertiesPath + "/config.properties")));
         
         PHABRICATOR_HOST_URL = props.getProperty("PHABRICATOR_HOST_URL");
         validatePropertyAvailable(PHABRICATOR_HOST_URL, "PHABRICATOR_HOST_URL");
            
         PHABRICATOR_USER_ID = props.getProperty("PHABRICATOR_USER_ID");
         validatePropertyAvailable(PHABRICATOR_USER_ID, "PHABRICATOR_USER_ID");
         
         CONDUIT_API_TOKEN = props.getProperty("CONDUIT_API_TOKEN");
         validatePropertyAvailable(CONDUIT_API_TOKEN, "CONDUIT_API_TOKEN");
         
         CONDUIT_CERTIFICATE = props.getProperty("CONDUIT_CERTIFICATE");
         validatePropertyAvailable(CONDUIT_CERTIFICATE, "CONDUIT_CERTIFICATE");
         
         if (props.getProperty("REPEAT_DELAY_SECONDS") != null)
         {
            REPEAT_DELAY_SECONDS = Integer.parseInt(props.getProperty("REPEAT_DELAY_SECONDS"));
         }
      }
      catch (FileNotFoundException e)
      {
         throw new RuntimeException(e);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
   }

   private void validatePropertyAvailable(String value, String invalidPropertyKey)
   {
      if (value == null || value.trim().isEmpty())
      {
         System.err.println("The key '"+invalidPropertyKey+"' was not set in "
               + "the configuration file (config.properties).");
         System.exit(1);
      }
   }
   
   private void initialiseSysTray()
   {
      // checking for support
      if (!SystemTray.isSupported())
      {
         return;
      }

      // get the systemTray of the system
      SystemTray systemTray = SystemTray.getSystemTray();

      URL iconUrl = PhabDiffNotifier.class.getResource("/images/cat_icon_16.png");
      Image image = Toolkit.getDefaultToolkit().getImage(iconUrl);

      // popupmenu
      PopupMenu trayPopupMenu = new PopupMenu();
      TrayIcon trayIcon = null;

      final String pauseOptionText = "Paws Notifications";
      final String unpauseOptionText = "Unpaws Notifications";
      final String exitApplicationText = "Exit Application";
      MenuItem action = new MenuItem(pauseOptionText);
      action.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            action.setLabel(isPawsed ? pauseOptionText : unpauseOptionText);
            isPawsed = !isPawsed;
         }
      });
      trayPopupMenu.add(action);

      MenuItem close = new MenuItem(exitApplicationText);
      close.addActionListener(new ActionListener()
      {
         @Override
         public void actionPerformed(ActionEvent e)
         {
            System.exit(0);
         }
      });
      trayPopupMenu.add(close);

      trayIcon = new TrayIcon(image, APPLICATION_NAME, trayPopupMenu);
      trayIcon.setImageAutoSize(true);

      try
      {
         systemTray.add(trayIcon);
      }
      catch (AWTException awtException)
      {
         awtException.printStackTrace();
      }
   }

   private void initialiseGrowl()
   {
      String name = APPLICATION_NAME;
      String notificationTypeId = "growlNotify";
      String iconUrl = null;
      String host = "localhost";
      int port = IProtocol.DEFAULT_GROWL_PORT;

      growl = new GrowlConnector(host, port);

      String appDataPath = System.getenv("APPDATA");
      String destinationPath = null;
      if (appDataPath != null && !appDataPath.isEmpty())
      {
         File iconDirectory = new File(appDataPath + File.separator + APPLICATION_NAME);
         try
         {
            destinationPath = exportResource("/images/cat_icon.png", iconDirectory);
         }
         catch (Exception e)
         {
         }
      }

      application = new Application(name, destinationPath);
      notificationType = new NotificationType(notificationTypeId, name, iconUrl);
      NotificationType[] notificationTypes = new NotificationType[] { notificationType };

      growl.register(application, notificationTypes);
   }

   private String exportResource(String resourceName, File destinationFolder) throws IOException
   {
      if (!destinationFolder.exists())
      {
         destinationFolder.mkdirs();
      }
      File iconFileDestination = new File(destinationFolder.getAbsoluteFile() + File.separator + "notify_icon.png");
      if (!iconFileDestination.exists())
      {
         URL inputUrl = PhabDiffNotifier.class.getResource(resourceName);
         FileUtils.copyURLToFile(inputUrl, iconFileDestination);
      }

      return iconFileDestination.getAbsolutePath();
   }

   private class DiffCheckTask extends TimerTask
   {
      private static final String NL = "\n";

      private ConduitAPIClient conduitClient;

      private Set<DiffInfo> mostRecentDiffs;

      public DiffCheckTask(ConduitAPIClient conduitClient)
      {
         this.conduitClient = conduitClient;
      }

      @Override
      public void run()
      {
         LocalDateTime timePoint = LocalDateTime.now();
         if (isPawsed || (sleepMode && (timePoint.getHour() >= 19 && timePoint.getHour() < 7)))
         {
            return;
         }
         try
         {
            JSONObject params = new JSONObject();
            params.element("status", "status-any").element("order", "order-modified").element("limit", String.valueOf(QUERY_LAST_NUM_ENTRIES));
            JSONObject jsonResponse = conduitClient.perform("differential.query", params);
            Map<String, DiffInfo> diffs = parseDiffInfos(jsonResponse);
            Set<String> newIds = getNewIDs(diffs);
            System.out.println(">> " + getCurrentTimeStamp() + " | Checking For Diffs. Number of updated IDs available: " + newIds.size());
            if (!newIds.isEmpty())
            {
               // update most recent diffs set
               mostRecentDiffs.clear();
               mostRecentDiffs.addAll(diffs.values());

               if (growl != null)
               {
                  for (String id : newIds)
                  {
                     DiffInfo d = diffs.get(id);
                     populateAuthorName(conduitClient, d);
                     StringBuilder message = new StringBuilder();
                     message.append("Author: ").append(d.authorName).append(NL)
                        .append(d.title).append(NL)
                        .append("Branch: ").append(d.branch).append(NL)
                        .append("Diff: D").append(d.id).append(NL)
                        .append("Created: ").append(FMT_DATE_AND_TIME.format(d.dateCreated)).append(NL)
                        .append("Last Mod: ").append(FMT_DATE_AND_TIME.format(d.lastModified)).append(NL);
                     Notification notification = new Notification(application, notificationType, "Differential Updated", message.toString());
                     notification.setURLCallback(d.uri);
                     notification.setPriority(0);
                     notification.setSticky(true);

                     growl.notify(notification);
                  }
               }
            }
         }
         catch (IOException e)
         {
            throw new RuntimeException(e);
         }
         catch (ConduitAPIException e)
         {
            throw new RuntimeException(e);
         }
      }

      private void populateAuthorName(ConduitAPIClient conduitClient, DiffInfo d)
      {
         JSONObject params = new JSONObject();
         JSONArray jsonArray = new JSONArray();
         jsonArray.add(d.authorPHID);
         params.element("phids", jsonArray);

         try
         {
            JSONObject jsonResponse = conduitClient.perform("user.query", params);
            Object userKeys = jsonResponse.get("result");
            if (userKeys instanceof JSONArray && !((JSONArray) userKeys).isEmpty())
            {
               Object userKey = ((JSONArray) userKeys).get(0);
               if (userKey instanceof JSONObject)
               {
                  JSONObject ju = (JSONObject) userKey;
                  String realName = ju.getString("realName");
                  if (realName != null && !realName.isEmpty())
                  {
                     d.authorName = realName;
                  }
               }
            }
         }
         catch (IOException e)
         {
            throw new RuntimeException(e);
         }
         catch (ConduitAPIException e)
         {
            throw new RuntimeException(e);
         }
      }

      private Set<String> getNewIDs(Map<String, DiffInfo> diffs)
      {
         if (mostRecentDiffs == null)
         {
            mostRecentDiffs = new LinkedHashSet<DiffInfo>(diffs.values());
            return mostRecentDiffs.stream().map(d->d.id).collect(Collectors.toSet());
         }

         Set<String> newIds = new LinkedHashSet<String>();
         newIds.addAll(diffs.keySet());
         
         Iterator<String> diffIter = diffs.keySet().iterator();
         while (diffIter.hasNext())
         {
            String diffId = diffIter.next();
            SameIDAndModDatePredicate pred = new SameIDAndModDatePredicate(diffs.get(diffId));
            boolean alreadyHandled = mostRecentDiffs.stream().anyMatch(pred);
            if (alreadyHandled)
            {
               newIds.remove(diffId);
            }
         }
         
         return newIds;
      }
   }

   private void start()
   {
      ConduitAPIClient conduitClient = new ConduitAPIClient(PHABRICATOR_HOST_URL, CONDUIT_API_TOKEN);

      DiffCheckTask task = new DiffCheckTask(conduitClient);
      Timer t = new Timer();
      int delay = Math.max(REPEAT_DELAY_SECONDS, MIN_REPEAT_DELAY_SECONDS);
      t.schedule(task, 0, delay);
   }

   private Map<String, DiffInfo> parseDiffInfos(JSONObject jsonResponse)
   {
      Map<String, DiffInfo> diffs = new LinkedHashMap<String, DiffInfo>();
      JSONArray diffArray = jsonResponse.getJSONArray("result");
      for (Object d : diffArray)
      {
         if (d instanceof JSONObject)
         {
            JSONObject jd = (JSONObject) d;
            DiffInfo di = new DiffInfo();
            di.id = jd.getString("id");
            di.phid = jd.getString("phid");
            di.title = jd.getString("title");
            di.uri = jd.getString("uri");
            String dateCreated = jd.getString("dateCreated");
            di.dateCreated = new Date(Long.valueOf(dateCreated) * 1000l);
            String lastModified = jd.getString("dateModified");
            di.lastModified = new Date(Long.valueOf(lastModified) * 1000l);
            di.authorPHID = jd.getString("authorPHID");
            di.branch = jd.getString("branch");
            diffs.put(di.id, di);
         }
      }

      return diffs;
   }

   private static String getCurrentTimeStamp()
   {
      SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      Date now = new Date();
      String strDate = sdfDate.format(now);
      return strDate;
   }

   protected JSONObject connect(ConduitAPIClient conduitClient)
   {
      long time = System.currentTimeMillis() / 1000;
      String authSignature = DigestUtils.sha1Hex(time + CONDUIT_CERTIFICATE);

      JSONObject params = new JSONObject();
      JSONObject jsonResponse = null;
      try
      {
         params.element("client", "conduitAC")
            .element("clientVersion", "1.2")
            .element("user", PHABRICATOR_USER_ID)
            .element("authToken", (int) time)
            .element("authSignature", authSignature)
            .element("host", PHABRICATOR_USER_ID);
         jsonResponse = conduitClient.perform("conduit.connect", params);
      }
      catch (IOException e)
      {
         throw new RuntimeException(e);
      }
      catch (ConduitAPIException e)
      {
         throw new RuntimeException(e);
      }

      return jsonResponse;
   }
}