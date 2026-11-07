package com.wig.tryon.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wig.tryon.model.Store;
import com.wig.tryon.model.WigAsset;
import com.wig.tryon.model.WigProduct;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版发型库 + 门店库（替代 MySQL，免外部依赖，便于快速演示）。
 * <p>
 * 商品（WigProduct）<b>仅</b>来自 data/customer-catalog.json — 运营在管理端上传本体时自动写入。
 * 内置 15 条种子商品已移除，避免清空后重启又冒出假数据。门店仍保留内置种子。
 */
@Repository
public class CatalogRepository {

    private static final Logger log = LoggerFactory.getLogger(CatalogRepository.class);

    private final ConcurrentHashMap<Long, WigProduct> products = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Store> stores = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Value("${app.storage.data-dir:./data}")
    private String dataDir;

    @PostConstruct
    public void seed() {
        seedStoresOnly();
        loadProductsFromCatalogFile();
    }

    private void seedStoresOnly() {
        addStore(s(1L, "朝阳旗舰体验中心", "朝阳区建国路88号SOHO现代城B座3层", "朝阳区", "0.8km",
                4.9, 168, "10:00 - 21:00", "010-8888-6666", true,
                List.of("旗舰店", "试戴体验", "专业定制"),
                List.of("/images/hair-natural.jpg", "/images/hair-curly.jpg", "/images/hair-short.jpg"),
                "王经理", "https://work.weixin.qq.com/ca/demo-chaoyang", 39.9200, 116.4530));

        addStore(s(2L, "望京精品体验店", "朝阳区望京SOHO T3座2层218室", "朝阳区", "1.5km",
                4.8, 96, "10:00 - 20:30", "010-8899-7766", true,
                List.of("精品店", "预约优先"),
                List.of("/images/hair-business.jpg", "/images/hair-natural.jpg", "/images/hair-curly.jpg"),
                "李顾问", "https://work.weixin.qq.com/ca/demo-wangjing", 40.0010, 116.4720));

        addStore(s(3L, "三里屯时尚体验馆", "朝阳区三里屯太古里南区S2-08", "朝阳区", "2.3km",
                4.7, 74, "11:00 - 22:00", "010-6455-8899", false,
                List.of("时尚地标", "网红打卡"),
                List.of("/images/hair-short.jpg", "/images/hair-business.jpg", "/images/hair-natural.jpg"),
                "陈顾问", "https://work.weixin.qq.com/ca/demo-sanlitun", 39.9330, 116.4470));

        addStore(s(4L, "国贸CBD专业定制中心", "朝阳区建国门外大街1号国贸商城B3-17", "朝阳区", "3.1km",
                4.9, 203, "09:30 - 21:30", "010-5918-6688", true,
                List.of("高端定制", "VIP专属"),
                List.of("/images/hair-business.jpg", "/images/hair-curly.jpg", "/images/hair-short.jpg"),
                "张总监", "https://work.weixin.qq.com/ca/demo-guomao", 39.9080, 116.4570));
    }

    /**
     * 热加载 customer-catalog.json（运营上传本体或批量导入后刷新 C 端，无需重启）。
     */
    public synchronized void reloadFromDisk() {
        products.clear();
        loadProductsFromCatalogFile();
        log.info("[CatalogRepository] 热加载完成，共 {} 条商品", products.size());
    }

    /** 运营上传/更新本体后，同步写入商品库并落盘 customer-catalog.json。 */
    public synchronized WigProduct upsertFromWigAsset(WigAsset asset) {
        if (asset == null || asset.id == null) {
            throw new IllegalArgumentException("WigAsset.id 不能为空");
        }
        WigProduct w = products.getOrDefault(asset.id, defaultProductForAsset(asset));
        w.id = asset.id;
        w.wigAssetId = asset.id;
        if (asset.name != null && !asset.name.isBlank()) {
            w.name = asset.name.replace("-本体", "").trim();
        }
        if (asset.category != null && !asset.category.isBlank()) {
            w.category = asset.category;
        }
        if (asset.descriptionForAi != null && !asset.descriptionForAi.isBlank()) {
            w.description = asset.descriptionForAi;
        }
        if (asset.colorFamily != null && !asset.colorFamily.isBlank()) {
            w.colorFamily = asset.colorFamily;
        }
        if (asset.tags != null && !asset.tags.isEmpty()) {
            w.tags = new ArrayList<>(asset.tags);
        }
        products.put(w.id, w);
        persistCatalogFile();
        return w;
    }

    /** 删除本体时同步下架商品。 */
    public synchronized void removeProduct(Long id) {
        if (id == null) return;
        if (products.remove(id) != null) {
            persistCatalogFile();
        }
    }

    /** 演示重置：清空全部商品（门店保留）。 */
    public synchronized void clearAllProducts() {
        products.clear();
        persistCatalogFile();
        log.warn("[CatalogRepository] 已清空全部商品");
    }

    private WigProduct defaultProductForAsset(WigAsset asset) {
        WigProduct w = new WigProduct();
        w.material = "100% 真人发丝";
        w.priceMin = 2800;
        w.priceMax = 5800;
        w.price = "¥2,800 - ¥5,800";
        w.tags = asset.tags != null ? new ArrayList<>(asset.tags) : new ArrayList<>(List.of("运营上传"));
        w.faceTypes = List.of("圆脸", "方脸", "鹅蛋脸", "长脸");
        w.rating = 4.8;
        w.reviews = 0;
        w.suitable = "多种脸型";
        w.weight = "约 110g";
        w.craft = "全手织";
        w.description = asset.descriptionForAi != null ? asset.descriptionForAi : "";
        w.colorFamily = asset.colorFamily != null ? asset.colorFamily : "自然黑";
        w.capStructure = "全手织";
        w.targetCrowds = List.of("客户演示");
        w.sceneTags = List.of("日常", "试戴");
        w.storeIds = List.of(1L, 2L);
        w.hairlineCoverage = 3;
        w.whiteHairCoverage = 3;
        w.isHot = false;
        w.isNew = true;
        w.image = "/images/hair-natural.jpg";
        w.refImage = w.image;
        w.seedResultImage = w.image;
        w.name = asset.name != null ? asset.name.replace("-本体", "").trim() : "款式 " + asset.id;
        w.category = asset.category != null && !asset.category.isBlank() ? asset.category : "中长发";
        return w;
    }

    private void loadProductsFromCatalogFile() {
        Path file = catalogFile();
        if (!Files.exists(file)) return;
        try {
            byte[] bytes = Files.readAllBytes(file);
            if (bytes.length == 0) return;
            List<Map<String, Object>> rows = objectMapper.readValue(bytes, new TypeReference<>() {});
            int n = 0;
            for (Map<String, Object> row : rows) {
                if (row.get("id") == null) continue;
                Long id = Long.valueOf(row.get("id").toString());
                WigProduct w = new WigProduct();
                w.id = id;
                w.wigAssetId = id;
                w.name = str(row, "name");
                w.category = str(row, "category");
                w.material = str(row, "material");
                w.priceMin = intVal(row, "priceMin", 2800);
                w.priceMax = intVal(row, "priceMax", 6800);
                w.price = String.format("¥%,d - ¥%,d", w.priceMin, w.priceMax);
                w.tags = strList(row, "tags");
                w.faceTypes = strList(row, "faceTypes");
                w.rating = dbl(row, "rating", 4.8);
                w.reviews = intVal(row, "reviews", 0);
                w.suitable = str(row, "suitable");
                w.weight = str(row, "weight");
                w.craft = str(row, "craft");
                w.description = str(row, "description");
                w.colorFamily = str(row, "colorFamily");
                w.capStructure = str(row, "capStructure");
                w.targetCrowds = strList(row, "targetCrowds");
                w.sceneTags = strList(row, "sceneTags");
                w.storeIds = longList(row, "storeIds");
                w.hairlineCoverage = intVal(row, "hairlineCoverage", 3);
                w.whiteHairCoverage = intVal(row, "whiteHairCoverage", 3);
                w.isHot = bool(row, "isHot");
                w.isNew = bool(row, "isNew");
                Object styleLine = row.get("styleLine");
                if (styleLine != null && !styleLine.toString().isBlank()) {
                    List<String> tags = w.tags == null ? new ArrayList<>() : new ArrayList<>(w.tags);
                    if (!tags.contains(styleLine.toString())) tags.add(styleLine.toString());
                    w.tags = tags;
                }
                w.image = str(row, "image");
                if (w.image == null || w.image.isBlank()) {
                    w.image = "/images/hair-natural.jpg";
                }
                w.refImage = w.image;
                w.seedResultImage = w.image;
                products.put(id, w);
                n++;
            }
            log.info("[CatalogRepository] 已加载 customer-catalog.json {} 条", n);
        } catch (IOException e) {
            log.warn("[CatalogRepository] 读取 customer-catalog.json 失败: {}", e.getMessage());
        }
    }

    private void persistCatalogFile() {
        try {
            Path dir = Paths.get(dataDir);
            Files.createDirectories(dir);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (WigProduct w : findAll()) {
                Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("id", w.id);
                row.put("name", w.name);
                row.put("category", w.category);
                row.put("material", w.material);
                row.put("priceMin", w.priceMin);
                row.put("priceMax", w.priceMax);
                row.put("tags", w.tags != null ? w.tags : List.of());
                row.put("faceTypes", w.faceTypes != null ? w.faceTypes : List.of());
                row.put("rating", w.rating);
                row.put("reviews", w.reviews);
                row.put("suitable", w.suitable);
                row.put("weight", w.weight);
                row.put("craft", w.craft);
                row.put("description", w.description);
                row.put("colorFamily", w.colorFamily);
                row.put("capStructure", w.capStructure);
                row.put("targetCrowds", w.targetCrowds != null ? w.targetCrowds : List.of());
                row.put("sceneTags", w.sceneTags != null ? w.sceneTags : List.of());
                row.put("storeIds", w.storeIds != null ? w.storeIds : List.of());
                row.put("hairlineCoverage", w.hairlineCoverage);
                row.put("whiteHairCoverage", w.whiteHairCoverage);
                row.put("isHot", w.isHot);
                row.put("isNew", w.isNew);
                row.put("sourceNote", "admin-wig-asset-sync");
                rows.add(row);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(catalogFile().toFile(), rows);
        } catch (IOException e) {
            log.error("[CatalogRepository] 写入 customer-catalog.json 失败", e);
            throw new RuntimeException("商品 catalog 持久化失败", e);
        }
    }

    private Path catalogFile() {
        return Paths.get(dataDir, "customer-catalog.json");
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static int intVal(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        if (v == null) return def;
        return Integer.parseInt(v.toString());
    }

    private static double dbl(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        if (v == null) return def;
        return Double.parseDouble(v.toString());
    }

    private static boolean bool(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null && Boolean.parseBoolean(v.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> strList(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> longList(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof List<?> list) {
            List<Long> out = new ArrayList<>();
            for (Object o : list) out.add(Long.valueOf(o.toString()));
            return out;
        }
        return List.of();
    }

    public List<WigProduct> findAll() {
        List<WigProduct> list = new ArrayList<>(products.values());
        list.sort((a, b) -> Long.compare(a.id, b.id));
        return list;
    }

    public List<WigProduct> find(String category, String faceType) {
        List<WigProduct> list = new ArrayList<>();
        for (WigProduct p : findAll()) {
            boolean catMatch = category == null || category.isBlank()
                    || "全部".equals(category) || p.category.equals(category);
            boolean faceMatch = faceType == null || faceType.isBlank()
                    || (p.faceTypes != null && p.faceTypes.contains(faceType));
            if (catMatch && faceMatch) {
                list.add(p);
            }
        }
        return list;
    }

    public Optional<WigProduct> findProduct(Long id) {
        return Optional.ofNullable(products.get(id));
    }

    public List<WigProduct> findHot(int limit) {
        return findAll().stream().filter(p -> p.isHot).limit(limit).toList();
    }

    public List<Store> findStores(int limit) {
        List<Store> list = new ArrayList<>(stores.values());
        list.sort((a, b) -> Double.compare(b.rating, a.rating));
        return list.stream().limit(limit).toList();
    }

    public Optional<Store> findStore(Long id) {
        return Optional.ofNullable(stores.get(id));
    }

    private void addStore(Store s) {
        stores.put(s.id, s);
    }

    private static Store s(Long id, String name, String address, String district, String distance,
                           double rating, int reviews, String hours, String phone, boolean isOpen,
                           List<String> tags, List<String> cases, String advisor, String wecomLink,
                           double latitude, double longitude) {
        Store st = new Store();
        st.id = id;
        st.name = name;
        st.address = address;
        st.district = district;
        st.distance = distance;
        st.rating = rating;
        st.reviews = reviews;
        st.hours = hours;
        st.phone = phone;
        st.isOpen = isOpen;
        st.image = "/images/store-interior.jpg";
        st.tags = tags;
        st.cases = cases;
        st.advisor = advisor;
        st.wecomLink = wecomLink;
        st.latitude = latitude;
        st.longitude = longitude;
        return st;
    }
}
