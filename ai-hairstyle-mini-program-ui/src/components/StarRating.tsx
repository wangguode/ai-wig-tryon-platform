import React, { useState } from 'react';
import { Star } from 'lucide-react';

interface StarRatingProps {
  rating?: number;
  maxRating?: number;
  size?: number;
  interactive?: boolean;
  onChange?: (rating: number) => void;
}

export const StarRating: React.FC<StarRatingProps> = ({
  rating = 0,
  maxRating = 5,
  size = 16,
  interactive = false,
  onChange,
}) => {
  const [hovered, setHovered] = useState(0);

  return (
    <div className="flex items-center gap-0.5">
      {Array.from({ length: maxRating }, (_, i) => {
        const filled = (interactive ? hovered || rating : rating) > i;
        return (
          <Star
            key={i}
            size={size}
            className={`${interactive ? 'cursor-pointer' : ''} transition-all duration-150`}
            fill={filled ? '#f59e0b' : 'none'}
            style={{
              color: filled ? '#f59e0b' : '#374151',
              filter: filled ? 'drop-shadow(0 0 4px rgba(245, 158, 11, 0.5))' : 'none',
            }}
            onMouseEnter={() => interactive && setHovered(i + 1)}
            onMouseLeave={() => interactive && setHovered(0)}
            onClick={() => interactive && onChange?.(i + 1)}
          />
        );
      })}
    </div>
  );
};
