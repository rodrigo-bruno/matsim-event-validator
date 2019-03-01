package ch.ethz.systems.hermes;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class App {

    public static final Map<String, Map<Integer, List<String>>> exp_agent_time_lines = new ConcurrentHashMap<>();
    public static final Map<String, Map<Integer, List<String>>> res_agent_time_lines = new ConcurrentHashMap<>();

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

            // TODO - need to remove events agents hoping into PT from PT agent.
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

    // step 2: check if, for every agent, the number of events is the same
    public static boolean validate_number() {
        boolean pass = true;
        Set<String> agents = exp_agent_time_lines.keySet();
        for (String agent : agents) {
            int exp_event_count = count_events(exp_agent_time_lines.get(agent));
            int res_event_count = count_events(res_agent_time_lines.get(agent));
            if (exp_event_count != res_event_count) {
                pass = false;
                System.out.println(String.format("number of events do not match (diff = %d) for agent %s",
                    exp_event_count - res_event_count, agent));
            }
        }
        return pass;
    }

    public static boolean compare_events(String agent) {
        int nlines = count_events(exp_agent_time_lines.get(agent));
        Map<Integer, List<String>> exp_time_lines = exp_agent_time_lines.get(agent);
        Map<Integer, List<String>> res_time_lines = res_agent_time_lines.get(agent);
        ArrayList<String> exp_sorted_lines = new ArrayList<>(nlines);
        ArrayList<String> res_sorted_lines = new ArrayList<>(nlines);

        // note, I only sort the time, not the actual content of the event. This
        // perserves that order by which the events were generated.
        for (Integer time : new TreeSet<>(exp_time_lines.keySet())) {
            exp_sorted_lines.addAll(exp_time_lines.get(time));
        }
        for (Integer time : new TreeSet<>(res_time_lines.keySet())) {
            res_sorted_lines.addAll(res_time_lines.get(time));
        }

        for (int i = 0; i < nlines; i++) {
            String[] exp_splits = exp_sorted_lines.get(i).split(" ");
            String[] res_splits = res_sorted_lines.get(i).split(" ");

            if (exp_splits.length != res_splits.length) {
                System.out.println(String.format("event do not match \n\t%s\n\t%s",
                    exp_sorted_lines.get(i), res_sorted_lines.get(i)));
                return false;
            }

            // Line syntax: <event time="39698.0" ... />
            for (int j = 0; j < exp_splits.length; j++) {
                // this is related to timing, which we are not testing.
                if (exp_splits[j].startsWith("time=") || exp_splits[j].startsWith("delay=")) {
                    continue;
                }
                // TODO - depending on the type of event, we might want to skip some other fields

                if (!exp_splits[j].equals(res_splits[j])) {
                    System.out.println(String.format("event do not match at token %d \n\t%s\n\t%s",
                        j, exp_sorted_lines.get(i), res_sorted_lines.get(i)));
                    return false;
                }
            }
        }
        return true;
    }

    // step 3; check if, the events are exactly the same when ignoring time
    public static boolean validate_events_notime() {
        AtomicBoolean pass = new AtomicBoolean(true);
        exp_agent_time_lines.keySet().parallelStream().forEach((String agent) -> {
            pass.set(compare_events(agent) && pass.get());
        });
        return pass.get();
    }

    // step 4: measure how off are we w.r.t. timming
    public static void measure_time_skew() {

    }

    public static void main(String[] args) throws Exception {
        String exp_tag = args[0];
        String exp_log = args[1];
        String res_tag = args[2];
        String res_log = args[3];
        log(String.format("Validating expected %s-%s against %s-%s",
            exp_tag, exp_log, res_tag, res_log));

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

        log("Checking number of events...");
        if (!validate_number()) {
            log("Checking number of events... Failed!");
            return;
        }
        log("Checking number of events... Done!");

        log("Checking events without time...");
        if (!validate_events_notime()) {
            log("Checking events without time... Failed!");
            return;
        }
        log("Checking events without time... Done!");
    }
}
