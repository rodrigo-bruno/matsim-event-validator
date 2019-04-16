package ch.ethz.systems.hermes;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class App {

    public static final Map<String, Map<Integer, List<String>>> exp_agent_time_lines = new ConcurrentHashMap<>();
    public static final Map<String, Map<Integer, List<String>>> res_agent_time_lines = new ConcurrentHashMap<>();
    public static final Map<String, Integer> exp_agent_traveltime = new ConcurrentHashMap();
    public static final Map<String, Integer> res_agent_traveltime = new ConcurrentHashMap();

    public static void log(String msg) {
        System.out.println(String.format("%s %s", new java.util.Date(), msg));
    }

    public static void load_lines(String file, List<String> array) throws Exception {
        try(BufferedReader br = new BufferedReader(new FileReader(file))) {
            for(String line; (line = br.readLine()) != null; array.add(line));
        }
    }

    public static String get_xml_field(String[] xml_splits, String field) {
        for (String split: xml_splits) {
            if (split.startsWith(field)) {
                return split.split("=")[1].replace("\"", "");
            }
        }
        return null;
    }

    public static void safe_add_entry(
                Map<String, Map<Integer, List<String>>> agent_time_lines,
                String agent, Integer time, String line) {
        // if the map does not contain the person yet
        if (!agent_time_lines.containsKey(agent)) {
            agent_time_lines.put(agent, new HashMap<Integer, List<String>>());
        }
        Map<Integer, List<String>> time_lines = agent_time_lines.get(agent);
        // if the maps does not contain the time yet
        if (!time_lines.containsKey(time)) {
            time_lines.put(time, new ArrayList<String>());
        }
        time_lines.get(time).add(line);
    }

    public static boolean process_lines(
            List<String> xml_lines, Map<String, Map<Integer, List<String>>> agent_time_lines) {
        AtomicBoolean ret = new AtomicBoolean(true);
        //xml_lines.parallelStream().forEach((String line) -> {
        xml_lines.stream().forEach((String line) -> {
            String[] splits = line.split(" ");
            Integer time = (int)Float.parseFloat(get_xml_field(splits, "time"));
            String type = get_xml_field(splits, "type");

            switch (type) {
                case "travelled" :
                case "arrival" :
                case "departure" :
                case "actend":
                case "actstart": {
                    String agent = get_xml_field(splits, "person");
                    if (agent == null) {
                        System.out.println(String.format("agent is null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, agent, time, line);
                    }
                    break;
                }
                case "VehicleArrivesAtFacility" :
                case "VehicleDepartsAtFacility" :
                case "enteredlink" :
                case "leftlink" : {
                    String agent = get_xml_field(splits, "vehicle");
                    if (agent == null) {
                        System.out.println(String.format("vehicle is null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, agent, time, line);
                    }
                    break;
                }
                case "vehicleenterstraffic" :
                case "vehicleleavestraffic" :
                case "PersonEntersVehicle" :
                case "PersonLeavesVehicle" : {
                    String person = get_xml_field(splits, "person");
                    String vehicle = get_xml_field(splits, "vehicle");
                    if (vehicle == null || person == null) {
                        System.out.println(String.format("vehicle and/or person is/are null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, person, time, line);
                        safe_add_entry(agent_time_lines, vehicle, time, line);
                    }
                    break;
                }
                case "TransitDriverStarts" : {
                    String driver = get_xml_field(splits, "driverId");
                    String vehicle = get_xml_field(splits, "vehicle");
                    if (vehicle == null || driver == null) {
                        System.out.println(String.format("vehicle and/or driver is/are null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, driver, time, line);
                        safe_add_entry(agent_time_lines, driver, time, line);
                    }
                    break;
                }
                case "waitingForPt" : {
                    String agent = get_xml_field(splits, "agent");
                    if (agent == null) {
                        System.out.println(String.format("agent it null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, agent, time, line);
                    }
                    break;
                }
                case "stuckAndAbort" : {
                    String person = get_xml_field(splits, "person");
                    if (person == null) {
                        System.out.println(String.format("person is null: %s", line));
                        ret.set(false);
                    } else {
                        safe_add_entry(agent_time_lines, person, time, line);
                    }
                    break;
                }
                default:
                    System.out.println(String.format("unknow event type: %s", line));
                    ret.set(false);
            }
        });
        return ret.get();
    }

    // step 1: check if the exp and result contain the same agents
    public static boolean validate_agents() {
        int exp_num_agents = exp_agent_time_lines.size();
        int res_num_agents = res_agent_time_lines.size();

        if (exp_agent_time_lines.keySet().equals(res_agent_time_lines.keySet())) {
            return true;
        }

        System.out.println(String.format("Number of agents: est=%d res=%d",
            res_num_agents, exp_num_agents));

        for (String res_agent : res_agent_time_lines.keySet()) {
            if (!exp_agent_time_lines.containsKey(res_agent)) {
                System.out.println(String.format("agent introduced: %s", res_agent));
            }
        }
        for (String exp_agent : exp_agent_time_lines.keySet()) {
            if (!res_agent_time_lines.containsKey(exp_agent)) {
                System.out.println(String.format("agent missing:  %s", exp_agent));
            }
        }

        return false;
    }

    public static int count_events(Map<Integer, List<String>> time_lines) {
        int count = 0;
        for (Map.Entry<Integer, List<String>> entry : time_lines.entrySet()) {
            count += entry.getValue().size();
        }
        return count;
    }

    // Removes fields from a line (event) or completely removes it.
    public static String sanitize_line(String agent, String line) {
        String[] splits = line.split(" ");
        String type = get_xml_field(splits, "type");

        // Ignoring person interactions with vehicles. This is because of PT interactions where we should not match the
        // vehicle (it might be different due to timing in schedules).
        if (type.equals("PersonLeavesVehicle") || type.equals("PersonEntersVehicle")) {
            return null;
        }

        for (int i = 0; i < splits.length; i++) {
            // Ignoring time and delay tags on events
            if (splits[i].startsWith("time=") || splits[i].startsWith("delay=")) {
                splits[i] = "";
            }

            // Ignoring elements from stuck and abort
            if (type.equals("stuckAndAbort") && (splits[i].startsWith("link=") || splits[i].startsWith("legMode="))) {
                splits[i] = "";
            }
        }
        return Arrays.stream(splits).collect(Collectors.joining(" "));
    }

    public static ArrayList<String> prepare_events(
            String agent, Map<Integer, List<String>> time_lines, Map<String, Integer> traveltime) {
        int nlines = count_events(time_lines);
        ArrayList<String> sorted_lines = new ArrayList<>(nlines);
        int start = 0;
        int finish = 0;

        // note, I only sort the time, not the actual content of the event. This
        // perserves that order by which the events were generated.
        for (Integer time : new TreeSet<>(time_lines.keySet())) {
            if (start == 0) {
                start = time;
            }
            finish = time;
            for (String line : time_lines.get(time)) {
                String sanitized = sanitize_line(agent, line);
                if (sanitized != null) {
                    sorted_lines.add(sanitized);
                }
            }
        }

        traveltime.put(agent, finish - start);

        return sorted_lines;
    }

    public static boolean compare_events(String agent) {
        ArrayList<String> exp_sorted_lines = prepare_events(agent, exp_agent_time_lines.get(agent), exp_agent_traveltime);
        ArrayList<String> res_sorted_lines = prepare_events(agent, res_agent_time_lines.get(agent), res_agent_traveltime);

        // check number of events
        if (exp_sorted_lines.size() != res_sorted_lines.size()) {
                System.out.println(String.format("number of events do not match (diff = %d) for agent %s",
                    exp_sorted_lines.size() - res_sorted_lines.size(), agent));
                return false;
        }

        // check actual event content
        for (int i = 0; i < exp_sorted_lines.size(); i++) {
            String[] exp_splits = exp_sorted_lines.get(i).split(" ");
            String[] res_splits = res_sorted_lines.get(i).split(" ");

            if (exp_splits.length != res_splits.length) {
                System.out.println(String.format("event do not match \n\t%s\n\t%s",
                    exp_sorted_lines.get(i), res_sorted_lines.get(i)));
                return false;
            }

            // Line syntax: <event time="39698.0" ... />
            for (int j = 0; j < exp_splits.length; j++) {
                if (!exp_splits[j].equals(res_splits[j])) {
                    System.out.println(String.format("event do not match at token %d \n\t%s\n\t%s",
                        j, exp_sorted_lines.get(i), res_sorted_lines.get(i)));
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean validate_events() {
        AtomicBoolean pass = new AtomicBoolean(true);
        exp_agent_time_lines.keySet().parallelStream().forEach((String agent) -> {
            pass.set(compare_events(agent) && pass.get());
        });
        return pass.get();
    }

    // step 4: measure how off are we w.r.t. timming
    public static void measure_time_skew() {
        // number of agents per percentage of time skew w.r.t. exp duration.
        int skew[] = new int[100];
        int num_agents = exp_agent_traveltime.size();

        for (String agent : exp_agent_traveltime.keySet()) {
            int exp_duration = exp_agent_traveltime.get(agent);
            int res_duration = res_agent_traveltime.get(agent);

            if (exp_duration == 0) {
                continue;
                // TODO - is this expected?
            }

            int skew_perc = Math.abs(exp_duration - res_duration) * 100 / exp_duration;
            skew[ Math.min(skew_perc, 99) ]++;
        }

        for (int i = 0; i < 100; i++) {
            System.out.println(String.format("skew %d percent is %d", i, (int)skew[i]));
        }

    }

    public static void main(String[] args) throws Exception {
        String exp_log = args[0];
        String res_log = args[1];
        log(String.format("Validating expected %s against %s", exp_log, res_log));

        {
            List<String> exp_lines = new ArrayList<>();
            List<String> res_lines = new ArrayList<>();

            log("Loading lines...");
            load_lines(exp_log, exp_lines);
            load_lines(res_log, res_lines);
            log("Loading lines... Done!");

            log("Processing exp lines...");
            if (!process_lines(exp_lines, exp_agent_time_lines)) {
                log("Processing exp lines... Failed!");
                return;
            }
            log("Processing exp lines... Done!");

            log("Processing res lines...");
            if (!process_lines(res_lines, res_agent_time_lines)) {
                log("Processing res lines... Failed!");
                return;
            }
            log("Processing res lines... Done!");
        }

        log("Checking number of agents...");
        if (!validate_agents()) {
            log("Checking number of agents... Failed!");
            return;
        }
        log("Checking number of agents... Done!");

        log("Checking events...");
        if (!validate_events()) {
            log("Checking events... Failed!");
        }
        log("Checking events... Done!");

        log("Measuring agent traveltimes...");
        measure_time_skew();
        log("Measuring agent traveltimes... Done!");
    }
}
