export interface HairStyle {
  id: number;
  name: string;
  category: string;
  material: string;
  price: string;
  priceMin: number;
  priceMax: number;
  image: string;
  tags: string[];
  rating: number;
  reviews: number;
  suitable: string;
  weight: string;
  craft: string;
  colorFamily?: string;
  capStructure?: string;
  targetCrowds?: string[];
  sceneTags?: string[];
  storeIds?: number[];
  hairlineCoverage?: number;
  whiteHairCoverage?: number;
  isHot?: boolean;
  isNew?: boolean;
  /** 该款式下全部成品图（10~15 张），后端 RecommendService 注入 */
  galleryImages?: string[];
}

export interface Store {
  id: number;
  name: string;
  address: string;
  district: string;
  distance: string;
  rating: number;
  reviews: number;
  hours: string;
  phone: string;
  isOpen: boolean;
  image: string;
  tags: string[];
  cases: string[];
  advisor: string;
}

export const hairStyles: HairStyle[] = [
  {
    id: 1,
    name: '柔光自然直发套',
    category: '长发',
    material: '100% 真人发丝',
    price: '¥3,800 - ¥6,500',
    priceMin: 3800,
    priceMax: 6500,
    image: '/images/hair-natural.jpg',
    tags: ['自然', '日常', '通用'],
    rating: 4.9,
    reviews: 328,
    suitable: '圆脸/方脸/长脸通用',
    weight: '约 120g，轻盈舒适',
    craft: '手工植发，发际线自然',
    isHot: true,
  },
  {
    id: 2,
    name: '甜美大波浪卷发',
    category: '卷发',
    material: '混合材质',
    price: '¥2,000 - ¥4,200',
    priceMin: 2000,
    priceMax: 4200,
    image: '/images/hair-curly.jpg',
    tags: ['时尚', '甜美', '浪漫'],
    rating: 4.8,
    reviews: 256,
    suitable: '长脸/鹅蛋脸最佳',
    weight: '约 150g',
    craft: '机器缝制，蓬松自然',
    isNew: true,
  },
  {
    id: 3,
    name: '都市精英短发套',
    category: '短发',
    material: '优质合成发丝',
    price: '¥1,500 - ¥3,000',
    priceMin: 1500,
    priceMax: 3000,
    image: '/images/hair-short.jpg',
    tags: ['简约', '干练', '商务'],
    rating: 4.7,
    reviews: 189,
    suitable: '方脸/圆脸修颜',
    weight: '约 80g，轻巧',
    craft: '精密剪裁，层次分明',
    isHot: true,
  },
  {
    id: 4,
    name: '优雅商务盘发套',
    category: '中长发',
    material: '100% 真人发丝',
    price: '¥4,500 - ¥8,000',
    priceMin: 4500,
    priceMax: 8000,
    image: '/images/hair-business.jpg',
    tags: ['商务', '高端', '正式'],
    rating: 4.9,
    reviews: 412,
    suitable: '各种脸型通用',
    weight: '约 130g',
    craft: '顶级手工工艺，丝滑质感',
    isNew: true,
  },
  {
    id: 5,
    name: '清纯蓬松中长发',
    category: '中长发',
    material: '混合材质',
    price: '¥1,800 - ¥3,500',
    priceMin: 1800,
    priceMax: 3500,
    image: '/images/tryon-result.jpg',
    tags: ['清纯', '学生', '日系'],
    rating: 4.6,
    reviews: 143,
    suitable: '圆脸/鹅蛋脸',
    weight: '约 110g',
    craft: '轻盈编织，透气性强',
  },
  {
    id: 6,
    name: '高贵御姐长直发',
    category: '长发',
    material: '100% 真人发丝',
    price: '¥5,000 - ¥9,000',
    priceMin: 5000,
    priceMax: 9000,
    image: '/images/hair-business.jpg',
    tags: ['高端', '御姐', '奢华'],
    rating: 5.0,
    reviews: 98,
    suitable: '长脸/鹅蛋脸',
    weight: '约 160g',
    craft: '进口发丝，顶级工艺',
    isHot: true,
  },
];

export const stores: Store[] = [
  {
    id: 1,
    name: '朝阳旗舰体验中心',
    address: '朝阳区建国路88号SOHO现代城B座3层',
    district: '朝阳区',
    distance: '0.8km',
    rating: 4.9,
    reviews: 168,
    hours: '10:00 - 21:00',
    phone: '010-8888-6666',
    isOpen: true,
    image: '/images/store-interior.jpg',
    tags: ['旗舰店', '试戴体验', '专业定制'],
    cases: ['/images/hair-natural.jpg', '/images/hair-curly.jpg', '/images/hair-short.jpg'],
    advisor: '王经理',
  },
  {
    id: 2,
    name: '望京精品体验店',
    address: '朝阳区望京SOHO T3座2层218室',
    district: '朝阳区',
    distance: '1.5km',
    rating: 4.8,
    reviews: 96,
    hours: '10:00 - 20:30',
    phone: '010-8899-7766',
    isOpen: true,
    image: '/images/store-interior.jpg',
    tags: ['精品店', '预约优先'],
    cases: ['/images/hair-business.jpg', '/images/hair-natural.jpg', '/images/hair-curly.jpg'],
    advisor: '李顾问',
  },
  {
    id: 3,
    name: '三里屯时尚体验馆',
    address: '朝阳区三里屯太古里南区S2-08',
    district: '朝阳区',
    distance: '2.3km',
    rating: 4.7,
    reviews: 74,
    hours: '11:00 - 22:00',
    phone: '010-6455-8899',
    isOpen: false,
    image: '/images/store-interior.jpg',
    tags: ['时尚地标', '网红打卡'],
    cases: ['/images/hair-short.jpg', '/images/hair-business.jpg', '/images/hair-natural.jpg'],
    advisor: '陈顾问',
  },
  {
    id: 4,
    name: '国贸CBD专业定制中心',
    address: '朝阳区建国门外大街1号国贸商城B3-17',
    district: '朝阳区',
    distance: '3.1km',
    rating: 4.9,
    reviews: 203,
    hours: '09:30 - 21:30',
    phone: '010-5918-6688',
    isOpen: true,
    image: '/images/store-interior.jpg',
    tags: ['高端定制', 'VIP专属'],
    cases: ['/images/hair-business.jpg', '/images/hair-curly.jpg', '/images/hair-short.jpg'],
    advisor: '张总监',
  },
];

export const categories = ['全部', '短发', '中长发', '长发', '卷发'];

export const historyItems = [
  { id: 1, date: '2024-01-15', hairName: '柔光自然直发套', image: '/images/hair-natural.jpg', rating: 5 },
  { id: 2, date: '2024-01-10', hairName: '甜美大波浪卷发', image: '/images/hair-curly.jpg', rating: 4 },
  { id: 3, date: '2024-01-05', hairName: '都市精英短发套', image: '/images/hair-short.jpg', rating: 4 },
];
