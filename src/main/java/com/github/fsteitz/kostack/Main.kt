package com.github.fsteitz.kostack;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Main {

  private static final int DUMP_COUNT = 6;                      // Iterationen
  private static final int DUMP_DELAY_S = 5;                    // In Sekunden
  private static final int DUMP_DELAY_MS = DUMP_DELAY_S * 1000; // In Millisekunden

  private static final String PROCESS_NAME_PART = "SHD ECORO";
  private static final String DUMP_FILE_PATTERN = "\\thread_%s_%s__%s.dump";

  public static void main(String[] args) {
    if (args.length == 0) {
      System.err.println("FEHLER: Es wurden keine Programmparameter uebergeben");
      System.exit(-1);
    } else if (args.length != 2) {
      System.err.println("FEHLER: Programmparameter sind ungültig");
      System.exit(-1);
    }

    createThreadDumps(args[0], args[1]);
  }

  private static void createThreadDumps(String jdkHome, String dumpFileBasePath) {
    System.out.println("JDK-Home: " + jdkHome);

    try {
      String jstackPattern = jdkHome + "\\jstack -l %s";
      List<String> ecoroPIDs = getEcoroPIDs();

      if (ecoroPIDs.size() == 0) {
        System.out.println("FEHLER: Es konnte kein aktiver Prozess von SHD ECORO ermittelt werden. Es wurden keine ThreadDumps erstellt!");
        System.exit(-1);
      }

      System.out.println("Alle ermittelten PIDs von SHD ECORO: " + ecoroPIDs.toString());

      for (String pid : ecoroPIDs) {
        createThreadDumps(pid, jstackPattern, dumpFileBasePath);
      }
    } catch (Exception e) {
      System.err.println("FEHLER: Die Datei konnte nicht generiert werden");
      e.printStackTrace();
    }
  }

  private static void createThreadDumps(String pid, String jstackPattern, String dumpFileBasePath) throws IOException, InterruptedException {
    System.out.println("Erzeuge insgesamt " + DUMP_COUNT + " ThreadDumps fuer PID '" + pid + "'");

    // Zähler beginnt bei 1 um nutzerfreundlicher zu sein
    for (int i = 1; i <= DUMP_COUNT; i++) {
      createThreadDump(pid, i, jstackPattern, dumpFileBasePath);
      System.out.println("ThreadDump " + i + " von " + DUMP_COUNT + " erzeugt. In " + DUMP_DELAY_S + " Sek. wird der naechste erzeugt");
      Thread.sleep(DUMP_DELAY_MS);
    }

    System.out.println("FERTIG: Alle ThreadDumps wurden erzeugt!");
  }

  private static void createThreadDump(String pid, int dumpIndex, String jstackPattern, String dumpFileBasePath) throws IOException {
    exec(String.format(jstackPattern, pid), stdin -> {
      try {
        String line;
        Path dumpFile = Path.of(String.format(dumpFileBasePath + DUMP_FILE_PATTERN, pid, System.currentTimeMillis(), dumpIndex));
        System.out.println("ThreadDump wird erzeugt: " + dumpFile.toString());

        if (!dumpFile.toFile().createNewFile()) {
          System.out.println("ThreadDump fuer PID '" + pid + "' konnte nicht erzeugt werden");
          return;
        }

        while ((line = stdin.readLine()) != null) {
          Files.writeString(dumpFile, line + '\n', StandardOpenOption.APPEND);
        }

        stdin.close();
      } catch (IOException e) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden");
        e.printStackTrace();
      }
    });
  }

  private static List<String> getEcoroPIDs() throws IOException {
    List<String> pids = new ArrayList<>();

    System.out.println("Ermittle PIDs von SHD ECORO");
    exec("tasklist /v /fo csv", stdin -> {
      String line;

      try {
        while ((line = stdin.readLine()) != null) {
          String[] columns = line.split(",");

          if (columns.length < 2) {
            System.err.println("FEHLER: Format von 'tasklist' ist ungueltig");
          } else if (columns[columns.length - 1] != null && columns[columns.length - 1].contains(PROCESS_NAME_PART)) {
            String pid = columns[1].replaceAll("\"", "");

            System.out.println("PID von SHD ECORO ermittelt: " + pid);
            pids.add(pid);
          }
        }
      } catch (IOException e) {
        System.err.println("FEHLER: ThreadDump konnte nicht erzeugt werden");
        e.printStackTrace();
      }
    });

    return pids;
  }

  private static void exec(String command, Consumer<BufferedReader> stdinConsumer) throws IOException {
    Process process = Runtime.getRuntime().exec(command);
    BufferedReader stdin = new BufferedReader(new InputStreamReader(process.getInputStream()));
    BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
    String line;

    // Read the output from the command
    stdinConsumer.accept(stdin);
    stdin.close();

    // Read any errors from the attempted command
    while ((line = stderr.readLine()) != null) {
      System.out.println(line);
    }

    stderr.close();
  }
}
