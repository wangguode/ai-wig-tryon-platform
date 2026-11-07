import type { HairStyle } from '../data/mockData';

/**
 * 千分位格式化：3800 -> "3,800"
 */
const fmt = (n: number): string => n.toLocaleString('en-US');

/**
 * 完整价格区间，用于详情卡 / 试戴弹层。
 * 例：3800-6500 -> "¥3,800 - ¥6,500"
 */
export const priceRangeFull = (hair: HairStyle): string => {
  if (hair.price && hair.price.includes('-')) return hair.price;
  if (hair.priceMin && hair.priceMax && hair.priceMin !== hair.priceMax) {
    return `¥${fmt(hair.priceMin)} - ¥${fmt(hair.priceMax)}`;
  }
  if (hair.priceMin) return `¥${fmt(hair.priceMin)}`;
  return hair.price || '';
};

/**
 * 紧凑价格区间，用于网格小卡（空间紧）。
 * 例：3800-6500 -> "¥3,800-6,500"
 */
export const priceRangeCompact = (hair: HairStyle): string => {
  if (hair.priceMin && hair.priceMax && hair.priceMin !== hair.priceMax) {
    return `¥${fmt(hair.priceMin)}-${fmt(hair.priceMax)}`;
  }
  if (hair.priceMin) return `¥${fmt(hair.priceMin)}`;
  return hair.price || '';
};
