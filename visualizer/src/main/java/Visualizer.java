package com.miner;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import redis.clients.jedis.Jedis;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class Visualizer {

    private static final String REDIS_HOST = System.getenv("REDIS_HOST");
    private static final String QUEUE_NAME = "word_queue";
    // Carpeta compartida dentro de Docker
    private static final String OUTPUT_PATH = "/app/charts/Ranking.png"; 

    private static Map<String, Integer> keywordCounts = new HashMap<>();

    public static void main(String[] args) {
        System.out.println("Visualizer (Modo Gráfico) iniciado.");
        System.out.println("Verificando carpeta de salida...");
        
        // Crear carpeta si no existe dentro del contenedor
        new File("/app/charts").mkdirs();

        try (Jedis jedis = new Jedis(REDIS_HOST, 6379)) {
            System.out.println("Conectado a Redis con éxito.");

            long lastChartTime = 0;

            while (true) {
                // Escuchar palabras de Redis 
                String keyword = jedis.blpop(0, QUEUE_NAME).get(1);
                keywordCounts.put(keyword, keywordCounts.getOrDefault(keyword, 0) + 1);

                // Actualizar gráfico  cada 3 segundos 
                if (System.currentTimeMillis() - lastChartTime > 3000) {
                    generateChart(keywordCounts);
                    lastChartTime = System.currentTimeMillis();
                }
            }
        } catch (Exception e) {
            System.err.println("Error en el Visualizer: " + e.getMessage());
        }
    }

    private static void generateChart(Map<String, Integer> data) {
        // Obtener Top 10 ordenado
        Map<String, Integer> topKeywords = data.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1, LinkedHashMap::new));

        // Crear dataset para el gráfico
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (Map.Entry<String, Integer> entry : topKeywords.entrySet()) {
            dataset.addValue(entry.getValue(), "Frecuencia", entry.getKey());
        }

        // Crear el gráfico de barras
        JFreeChart chart = ChartFactory.createBarChart(
                "Top 10 Palabras Clave en Métodos (GitHub)", // Título
                "Palabra",                                 // Eje X
                "Frecuencia",                             // Eje Y
                dataset,
                PlotOrientation.HORIZONTAL,               // Barras horizontales
                false, true, false);

        // Personalizar estilos básicos
        chart.setBackgroundPaint(Color.white);
        chart.getCategoryPlot().setRangeGridlinePaint(Color.gray);

        // Guardar como imagen PNG
        try {
            File outputFile = new File(OUTPUT_PATH);
            ChartUtils.saveChartAsPNG(outputFile, chart, 1024, 768);
            System.out.print("."); // Imprimir un punto en la terminal para saber que actualizó
        } catch (IOException e) {
            System.err.println("\nError guardando gráfico: " + e.getMessage());
        }
    }
}