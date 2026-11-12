package com.power.rag.splitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class SplitterCheck {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of("data/docs");
        int files = 0, total = 0;
        System.out.printf("%-22s %6s %8s%n", "文件", "块数", "字数");
        try (Stream<Path> fs = Files.list(dir)) {
            List<Path> list = fs.filter(p -> p.toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path f : list) {
                List<String> c = Splitter.byHeading(Files.readString(f));
                int chars = c.stream().mapToInt(String::length).sum();
                System.out.printf("%-22s %6d %8d%n", f.getFileName(), c.size(), chars);
                files++;
                total += c.size();
                if (files <= 2) {
                    System.out.println("  --- 第 1 块预览 ---");
                    System.out.println("  " + c.get(0).replace("\n", "\n  "));
                }
            }
        }
        System.out.printf("%n合计：%d 份，%d 块，平均 %.1f 块/份%n",
                files, total, total * 1.0 / files);
    }
}
