import { Menu } from '@headlessui/react';
import { Icon } from '../Icon/Icon';
import { ReactNode, useEffect, useRef, useState } from 'react';

// 타입 정의
type StarValue = { type: 'star'; count: 1 | 2 | 3 };
type DropdownValue = string | number | StarValue | ReactNode;

interface DropdownItem {
  label: string;
  value: DropdownValue;
  icon?: ReactNode;
}

interface DropdownProps {
  label?: string;
  placeholder?: string;
  value?: DropdownValue;
  items: DropdownItem[];
  onChange?: (value: DropdownValue) => void;
  isDisabled?: boolean;
  className?: string;
}

const Dropdown = ({
  label,
  placeholder = '선택해주세요',
  value,
  items,
  onChange,
  isDisabled = false,
  className = '',
}: DropdownProps) => {
  const [position, setPosition] = useState<'top' | 'bottom'>('bottom');
  const buttonRef = useRef<HTMLButtonElement>(null);

  const selectedItem = items.find((item) => {
    if (
      typeof item.value === 'object' &&
      item.value !== null &&
      'type' in item.value &&
      item.value.type === 'star'
    ) {
      return (
        value !== undefined &&
        value !== null &&
        typeof value === 'object' &&
        'type' in value &&
        value.type === 'star' &&
        (value as StarValue).count === (item.value as StarValue).count
      );
    }
    return item.value === value;
  });

  useEffect(() => {
    const calculatePosition = () => {
      if (!buttonRef.current) return;
      const rect = buttonRef.current.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      const spaceAbove = rect.top;
      setPosition(spaceBelow < 200 && spaceAbove > spaceBelow ? 'top' : 'bottom');
    };

    calculatePosition();
    window.addEventListener('scroll', calculatePosition);
    window.addEventListener('resize', calculatePosition);
    return () => {
      window.removeEventListener('scroll', calculatePosition);
      window.removeEventListener('resize', calculatePosition);
    };
  }, []);

  const renderStars = (count: number) => {
    return Array(count)
      .fill(0)
      .map((_, index) => <Icon key={index} name='Star' size={16} />);
  };

  const renderValue = (item: DropdownItem) => {
    if (
      typeof item.value === 'object' &&
      item.value !== null &&
      'type' in item.value &&
      item.value.type === 'star'
    ) {
      return (
        <div className='flex items-center gap-1'>
          {renderStars((item.value as StarValue).count)}
          <span className='ml-2'>{item.label}</span>
        </div>
      );
    }
    return (
      <div className='flex items-center gap-2'>
        {item.icon}
        <span>{item.label}</span>
      </div>
    );
  };

  return (
    <div className={`relative inline-block ${className}`}>
      {label && <label className='block text-p2 font-medium text-dark-grey mb-2'>{label}</label>}
      <Menu as='div' className='relative'>
        {({ open }) => (
          <>
            <Menu.Button
              ref={buttonRef}
              disabled={isDisabled}
              className={`
                inline-flex w-full items-center justify-between gap-x-1.5 rounded-lg px-3 py-2 text-p1
                ${
                  isDisabled
                    ? 'bg-broken-white text-grey cursor-not-allowed'
                    : 'bg-broken-white text-black hover:bg-broken-white'
                }
                border border-broken-white transition-colors
              `}
            >
              {selectedItem ? (
                renderValue(selectedItem)
              ) : (
                <span className='text-grey text-p2'>{placeholder}</span>
              )}
              <Icon
                name={open ? 'ArrowUp2' : 'ArrowDown2'}
                size={20}
                color='grey'
                className='transition-transform'
              />
            </Menu.Button>

            <Menu.Items
              className={`
                absolute ${position === 'top' ? 'bottom-full mb-1' : 'top-full mt-1'}
                left-0 z-10 w-full origin-top-right rounded-lg bg-white shadow-lg
                border border-grey-30 focus:outline-none
                transition-all duration-200 ease-out
              `}
            >
              <div className='py-1'>
                {items.map((item, index) => (
                  <Menu.Item key={index} as='div'>
                    {({ active }) => (
                      <button
                        type='button'
                        className={`
                          w-full px-4 py-2 text-left text-p2
                          ${active ? 'bg-broken-white text-black' : 'text-dark-grey'}
                          ${index === 0 ? 'rounded-t-lg' : ''}
                          ${index === items.length - 1 ? 'rounded-b-lg' : ''}
                        `}
                        onClick={() => onChange?.(item.value)}
                      >
                        {renderValue(item)}
                      </button>
                    )}
                  </Menu.Item>
                ))}
              </div>
            </Menu.Items>
          </>
        )}
      </Menu>
    </div>
  );
};

export type { DropdownProps, DropdownItem, DropdownValue };
export default Dropdown;
