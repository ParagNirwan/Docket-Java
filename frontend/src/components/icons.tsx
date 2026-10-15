import type { SVGProps } from 'react';
import {
  Menu as MenuIconLucide,
  X as CloseIconLucide,
  Settings as SettingsIconLucide,
  LogOut as LogOutIconLucide,
  User as UserIconLucide,
  Building2 as BuildingIconLucide,
  Plus as PlusIconLucide,
  ShieldCheck as ShieldIconLucide,
  ChevronDown as ChevronDownIconLucide,
  ChevronRight as ChevronRightIconLucide,
  Check as CheckIconLucide,
  FolderArchive as FolderArchiveIconLucide,
  ServerNas as ServerNasLucide
} from 'lucide-react';

export const LogoIcon = (props: SVGProps<SVGSVGElement>) => <FolderArchiveIconLucide className='w-7 h-7' {...props} />;

export const MenuIcon = (props: SVGProps<SVGSVGElement>) => <MenuIconLucide className='w-6 h-6' {...props} />;
export const CloseIcon = (props: SVGProps<SVGSVGElement>) => <CloseIconLucide className='w-6 h-6' {...props} />;
export const SettingsIcon = (props: SVGProps<SVGSVGElement>) => <SettingsIconLucide className='w-5 h-5' {...props} />;
export const LogOutIcon = (props: SVGProps<SVGSVGElement>) => <LogOutIconLucide className='w-5 h-5' {...props} />;
export const UserIcon = (props: SVGProps<SVGSVGElement>) => <UserIconLucide className='w-5 h-5' {...props} />;
export const BuildingIcon = (props: SVGProps<SVGSVGElement>) => <BuildingIconLucide className='w-5 h-5' {...props} />;
export const PlusIcon = (props: SVGProps<SVGSVGElement>) => <PlusIconLucide className='w-5 h-5' {...props} />;
export const ShieldIcon = (props: SVGProps<SVGSVGElement>) => <ShieldIconLucide className='w-5 h-5' {...props} />;
export const ChevronDownIcon = (props: SVGProps<SVGSVGElement>) => <ChevronDownIconLucide className='w-4 h-4' {...props} />;
export const ChevronRightIcon = (props: SVGProps<SVGSVGElement>) => <ChevronRightIconLucide className='w-4 h-4' {...props} />;
export const CheckIcon = (props: SVGProps<SVGSVGElement>) => <CheckIconLucide className='w-4 h-4' {...props} />;
export const ServerNas =  (props: SVGProps<SVGSVGElement>) => <ServerNasLucide className='w-4 h-4' {...props} />;
