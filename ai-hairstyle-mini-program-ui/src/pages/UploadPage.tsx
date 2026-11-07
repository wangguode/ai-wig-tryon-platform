import React, { useState, useRef } from 'react';
import { Camera, Image, Shield, CheckCircle, AlertCircle, X, Upload, Sparkles } from 'lucide-react';
import { PageHeader } from '../components/PageHeader';
import { api } from '../api/client';

interface UploadPageProps {
  onBack: () => void;
  onNext: (
    imageData: string,
    sessionId: string | null,
    faceCheckStatus?: 'PENDING' | 'PASSED' | 'FAILED',
  ) => void;
}

export const UploadPage: React.FC<UploadPageProps> = ({ onBack, onNext }) => {
  const [uploadedImage, setUploadedImage] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const tips = [
    '面向镜头，保持正脸',
    '避免强逆光环境',
    '尽量露出完整额头',
    '图片大小 ≤ 10MB',
  ];

  const handleFileSelect = (file: File) => {
    setError(null);

    // Validate
    if (file.size > 10 * 1024 * 1024) {
      setError('图片大小不能超过 10MB');
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      setError('仅支持 JPG / PNG / WebP 格式');
      return;
    }

    setSelectedFile(file);
    const reader = new FileReader();
    reader.onload = (e) => {
      setUploadedImage(e.target?.result as string);
    };
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    const file = e.dataTransfer.files[0];
    if (file) handleFileSelect(file);
  };

  const handleFileInput = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) handleFileSelect(file);
  };

  const handleConfirm = async () => {
    if (!uploadedImage) return;
    setChecking(true);
    setError(null);
    try {
      if (selectedFile) {
        // 真正上传到后端，做人脸预检并拿到 sessionId
        const res = await api.uploadPhoto(selectedFile);
        api.submitFeedback({
          sessionId: res.sessionId,
          eventType: 'upload_photo',
          sentiment: 'event',
        }).catch(() => {});
        setChecking(false);
        onNext(uploadedImage, res.sessionId, res.faceCheckStatus);
        return;
      }
      throw new Error('no file');
    } catch (e: any) {
      // 后端不可用或人脸未通过：若是后端拒绝则提示，否则降级为离线演示流程
      const msg = e?.message || '';
      if (msg && !msg.startsWith('HTTP') && msg !== 'no file' && msg !== 'Failed to fetch') {
        setChecking(false);
        setError(msg);
        return;
      }
      // 离线降级：继续走演示流程（sessionId 为空）
      api.submitFeedback({
        eventType: 'upload_photo',
        sentiment: 'event',
        note: 'offline_fallback',
      }).catch(() => {});
      setChecking(false);
      onNext(uploadedImage, null);
    }
  };

  return (
    <div className="page-enter pb-24">
      <PageHeader title="上传照片" onBack={onBack} />

      {/* Upload area */}
      <div className="px-4 mt-4">
        <div
          className={`relative rounded-2xl overflow-hidden transition-all duration-300 cursor-pointer ${isDragging ? 'scale-105' : ''}`}
          style={{
            border: `2px dashed ${isDragging ? '#00d4ff' : uploadedImage ? 'rgba(0,255,150,0.4)' : 'rgba(0,212,255,0.25)'}`,
            background: isDragging
              ? 'rgba(0,212,255,0.05)'
              : 'rgba(10,22,40,0.6)',
            minHeight: 240,
            boxShadow: isDragging ? '0 0 30px rgba(0,212,255,0.2)' : 'none',
          }}
          onDragOver={(e) => { e.preventDefault(); setIsDragging(true); }}
          onDragLeave={() => setIsDragging(false)}
          onDrop={handleDrop}
          onClick={() => !uploadedImage && fileInputRef.current?.click()}
        >
          {uploadedImage ? (
            <>
              <img src={uploadedImage} alt="Uploaded" className="w-full h-64 object-cover" />
              <div
                className="absolute inset-0"
                style={{ background: 'linear-gradient(to top, rgba(5,10,20,0.8), transparent)' }}
              />
              <button
                onClick={(e) => { e.stopPropagation(); setUploadedImage(null); }}
                className="absolute top-3 right-3 p-1.5 rounded-full"
                style={{ background: 'rgba(5,10,20,0.8)', border: '1px solid rgba(255,100,100,0.4)' }}
              >
                <X size={14} style={{ color: '#ff6464' }} />
              </button>
              <div className="absolute bottom-3 left-3 flex items-center gap-1.5">
                <CheckCircle size={14} style={{ color: '#00ff96' }} />
                <span className="text-xs font-medium" style={{ color: '#00ff96' }}>照片已选择</span>
              </div>
              {/* Scan line effect */}
              <div
                className="absolute left-0 right-0 h-0.5 scan-line"
                style={{ background: 'linear-gradient(90deg, transparent, #00d4ff80, transparent)' }}
              />
            </>
          ) : (
            <div className="flex flex-col items-center justify-center h-60 gap-3 p-6">
              {/* Animated icon */}
              <div className="relative">
                <div
                  className="w-20 h-20 rounded-full flex items-center justify-center"
                  style={{ background: 'rgba(0,212,255,0.08)', border: '2px solid rgba(0,212,255,0.2)' }}
                >
                  <Camera size={32} style={{ color: '#00d4ff' }} />
                </div>
                <div
                  className="absolute inset-0 rounded-full ripple"
                  style={{ border: '2px solid rgba(0,212,255,0.3)' }}
                />
                <div
                  className="absolute inset-0 rounded-full ripple"
                  style={{ border: '2px solid rgba(0,212,255,0.15)', animationDelay: '0.5s' }}
                />
              </div>
              <div className="text-center">
                <p className="text-sm font-semibold text-white mb-1">点击上传正面照片</p>
                <p className="text-xs" style={{ color: '#4b5563' }}>或拖拽图片到此处</p>
              </div>
              <div className="flex gap-2 flex-wrap justify-center">
                <span className="chip chip-blue">JPG</span>
                <span className="chip chip-blue">PNG</span>
                <span className="chip chip-blue">WebP</span>
                <span className="chip chip-purple">≤ 10MB</span>
              </div>
            </div>
          )}
        </div>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          onChange={handleFileInput}
          className="hidden"
        />

        {/* Error */}
        {error && (
          <div
            className="flex items-center gap-2 mt-2 px-3 py-2 rounded-lg"
            style={{ background: 'rgba(255,100,100,0.1)', border: '1px solid rgba(255,100,100,0.25)' }}
          >
            <AlertCircle size={14} style={{ color: '#ff6464' }} />
            <span className="text-xs" style={{ color: '#ff6464' }}>{error}</span>
          </div>
        )}
      </div>

      {/* Tips */}
      <div className="px-4 mt-4">
        <div
          className="rounded-xl p-3"
          style={{ background: 'rgba(0,212,255,0.05)', border: '1px solid rgba(0,212,255,0.12)' }}
        >
          <div className="flex items-center gap-2 mb-2">
            <Sparkles size={13} style={{ color: '#00d4ff' }} />
            <span className="text-xs font-semibold" style={{ color: '#00d4ff' }}>拍照效果更佳提示</span>
          </div>
          <div className="grid grid-cols-2 gap-1.5">
            {tips.map((tip, i) => (
              <div key={i} className="flex items-center gap-1.5">
                <div className="w-1 h-1 rounded-full flex-shrink-0" style={{ background: '#00d4ff' }} />
                <span className="text-xs" style={{ color: '#94a3b8' }}>{tip}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Action buttons */}
      <div className="px-4 mt-4 grid grid-cols-2 gap-3">
        <button
          className="flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-medium transition-all"
          style={{
            background: 'rgba(10,22,40,0.8)',
            border: '1px solid rgba(0,212,255,0.25)',
            color: '#00d4ff',
          }}
          onClick={() => fileInputRef.current?.click()}
        >
          <Camera size={16} />
          立即拍照
        </button>
        <button
          className="flex items-center justify-center gap-2 py-3 rounded-xl text-sm font-medium transition-all"
          style={{
            background: 'rgba(10,22,40,0.8)',
            border: '1px solid rgba(179,71,255,0.25)',
            color: '#b347ff',
          }}
          onClick={() => fileInputRef.current?.click()}
        >
          <Image size={16} />
          从相册选
        </button>
      </div>

      {/* Privacy notice */}
      <div className="px-4 mt-3">
        <div
          className="flex items-start gap-2 px-3 py-2.5 rounded-xl"
          style={{ background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.06)' }}
        >
          <Shield size={14} style={{ color: '#6b7280', flexShrink: 0, marginTop: 1 }} />
          <p className="text-xs leading-relaxed" style={{ color: '#4b5563' }}>
            🔒 您的照片仅用于本次 AI 试戴生成，<span style={{ color: '#94a3b8' }}>24小时后自动删除</span>，我们不会存储或分享您的照片。
          </p>
        </div>
      </div>

      {/* Confirm button */}
      {uploadedImage && (
        <div className="px-4 mt-4">
          <button
            onClick={handleConfirm}
            disabled={checking}
            className="w-full py-3.5 rounded-xl text-sm font-bold flex items-center justify-center gap-2 btn-primary transition-all"
            style={{ color: '#050a14' }}
          >
            {checking ? (
              <>
                <div
                  className="w-4 h-4 rounded-full border-2 border-t-transparent animate-spin"
                  style={{ borderColor: '#050a14', borderTopColor: 'transparent' }}
                />
                上传中...
              </>
            ) : (
              <>
                <Upload size={16} />
                确认上传，选择发型
              </>
            )}
          </button>
        </div>
      )}
    </div>
  );
};
