package com.wig.tryon.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 客户素材文件夹分类规则（与 scripts/lib/customer_catalog_v3.py 对齐）。 */
public final class CustomerCatalogRules {

    private static final List<String> WIG_PRIORITY = List.of(
            "正_C.png", "正_C.jpg", "0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG");
    private static final Set<String> WIG_BARE_EXACT = Set.of(
            "0_C.png", "0_C.jpg", "1_C.jpg", "1_C.JPG", "正_C.png", "正_C.jpg", "后_C.png", "后_C.jpg");
    private static final Pattern F6A_SHOWCASE = Pattern.compile("0F6A\\d+_C\\.(jpg|jpeg|png)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern V_CODE = Pattern.compile("(V\\d+)");

    private CustomerCatalogRules() {
    }

    public static Optional<String> pickWigBareFileName(List<String> names) {
        for (String fn : WIG_PRIORITY) {
            if (names.contains(fn)) {
                return Optional.of(fn);
            }
        }
        return names.stream().filter(CustomerCatalogRules::isWigBareFileName).sorted().findFirst();
    }

    public static boolean isWigBareFileName(String name) {
        if (name.toUpperCase(Locale.ROOT).startsWith("IMG_")) {
            return false;
        }
        return WIG_BARE_EXACT.contains(name);
    }

    public static boolean isShowcaseFileName(String name) {
        if (name.contains("副本")) {
            return false;
        }
        if (name.toUpperCase(Locale.ROOT).startsWith("IMG_")) {
            return true;
        }
        if (F6A_SHOWCASE.matcher(name).matches()) {
            return true;
        }
        return name.matches("(?i).*_C\\.(jpg|jpeg|png)$") && !WIG_BARE_EXACT.contains(name);
    }

    public static List<String> showcaseNamesFromFolder(List<String> names, String wigBareName) {
        List<String> imgs = names.stream()
                .filter(n -> !WIG_BARE_EXACT.contains(n))
                .filter(n -> !n.equals(wigBareName))
                .filter(n -> !n.contains("副本"))
                .filter(CustomerCatalogRules::isShowcaseFileName)
                .sorted(Comparator
                        .comparing((String n) -> n.toUpperCase(Locale.ROOT).startsWith("IMG_") ? 0 : 1)
                        .thenComparing(n -> n.toLowerCase(Locale.ROOT)))
                .toList();
        return imgs.size() > 15 ? imgs.subList(0, 15) : imgs;
    }

    public static String vCodeFromFolder(String folderName) {
        var m = V_CODE.matcher(folderName.replace(" ", ""));
        return m.find() ? m.group(1) : folderName;
    }

    public static List<String> listImageNames(Path folder) throws java.io.IOException {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(folder)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String lower = n.toLowerCase(Locale.ROOT);
                        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png");
                    })
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    public record StylePreview(String folderName, String code, String bareFile, int showcaseCount, List<String> warnings) {
    }

    public static List<StylePreview> scanVFolders(Path root) throws java.io.IOException {
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("素材目录不存在: " + root);
        }
        List<StylePreview> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> folders = stream.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("V"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path folder : folders) {
                List<String> names = listImageNames(folder);
                Optional<String> bare = pickWigBareFileName(names);
                List<String> warnings = new ArrayList<>();
                if (bare.isEmpty()) {
                    warnings.add("无本体图");
                    out.add(new StylePreview(folder.getFileName().toString(), vCodeFromFolder(folder.getFileName().toString()),
                            null, 0, warnings));
                    continue;
                }
                List<String> showcase = showcaseNamesFromFolder(names, bare.get());
                if (showcase.size() < 8) {
                    warnings.add("陈列仅 " + showcase.size() + " 张");
                }
                out.add(new StylePreview(folder.getFileName().toString(), vCodeFromFolder(folder.getFileName().toString()),
                        bare.get(), showcase.size(), warnings));
            }
        }
        return out;
    }
}
