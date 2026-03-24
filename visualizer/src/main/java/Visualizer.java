import redis.clients.jedis.Jedis;
import java.util.*;

public class Visualizer {
    public static void main(String[] args) {
        Jedis jedis = new Jedis("redis", 6379);
        Map<String, Integer> ranking = new HashMap<>();
        System.out.println("Visualizer: Esperando datos...");

        while (true) {
            List<String> res = jedis.brpop(0, "word_queue");
            String word = res.get(1);
            ranking.put(word, ranking.getOrDefault(word, 0) + 1);
            
            // Imprimir Top 10
            System.out.println("\n--- TOP 10 PALABRAS ---");
            ranking.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> System.out.println(e.getKey() + ": " + e.getValue()));
        }
    }
}